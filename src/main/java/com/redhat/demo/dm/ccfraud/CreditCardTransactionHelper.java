package com.redhat.demo.dm.ccfraud;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.redhat.demo.dm.ccfraud.domain.CreditCardTransaction;
import com.redhat.demo.dm.ccfraud.domain.PotentialFraudFact;
import com.redhat.demo.dm.ccfraud.integration.kie.BusinessAutomationClient;

import org.kie.api.KieServices;
import org.kie.api.builder.KieScanner;
import org.kie.api.builder.ReleaseId;
import org.kie.api.cdi.KReleaseId;
import org.kie.api.cdi.KSession;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.EntryPoint;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.time.SessionClock;
import org.kie.api.time.SessionPseudoClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service("creditCardTransactionHelper")
public class CreditCardTransactionHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreditCardTransactionHelper.class);
    private static final DateTimeFormatter DATE_TIME_FORMAT = 
        DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss:SSS", Locale.US);

    // private KieContainer kContainer;
    // @KReleaseId( groupId = "jar1", artifactId = "art1", version = "1.0")
    @KSession("cdfd-session")
    private KieSession kieSession;

    @Value("${kie.process.container.id}") 
    String processContainerId;
    @Value("${kie.process.definition.id}") 
    String processDefinitionId;
        
    @Autowired
    private CreditCardTransactionRepository cctRepository;
    @Autowired
    private BusinessAutomationClient baClient;

    public void processTransaction(CreditCardTransaction ccTransaction) {
        // Retrieve all transactions for this account
        Collection<CreditCardTransaction> ccTransactions = cctRepository
                .getCreditCardTransactionsForCC(ccTransaction.getCreditCardNumber());

        if (ccTransactions == null) {
            LOGGER.info("no previous transactions found for Credit Card {}", ccTransaction.getCreditCardNumber());
            return;
        }

        LOGGER.info("Found '" + ccTransactions.size() + 
            "' transactions for creditcard: '" + ccTransaction.getCreditCardNumber() + "'.");

        //KieSession kieSession = createKieSession("cdfd-session");

        // Insert transaction history/context.
        LOGGER.info("Inserting previous (recent) credit card transactions into session.");
        for (CreditCardTransaction nextTransaction : ccTransactions) {
            insert(kieSession, "Transactions", nextTransaction);
        }

        // Insert the new transaction event
        LOGGER.info(" ");
        LOGGER.info("Inserting credit card transaction event into session.");
        insert(kieSession, "Transactions", ccTransaction);

        // And fire the rules.
        LOGGER.info("Firing the rules on Session [ {} ] WM", kieSession.getIdentifier());
        int fired = kieSession.fireAllRules();
        LOGGER.info("{} rules got fired!", fired);

        Collection<?> fraudResponse = kieSession.getObjects();
        for (Object fact : fraudResponse) {
            PotentialFraudFact potentialFraudFact = (PotentialFraudFact)fact;
            LOGGER.info(potentialFraudFact.toString());

            createCaseForPotentialFraud(potentialFraudFact);
        }

        // Dispose the session to free up the resources.
        kieSession.dispose();
    }

	private static FactHandle insert(KieSession kieSession, String stream, CreditCardTransaction cct) {
		SessionClock clock = kieSession.getSessionClock();
		if (!(clock instanceof SessionPseudoClock)) {
			String errorMessage = "This fact inserter can only be used with KieSessions that use a SessionPseudoClock";
			LOGGER.error(errorMessage);
			throw new IllegalStateException(errorMessage);
        }
        
        SessionPseudoClock pseudoClock = (SessionPseudoClock) clock;
        String dateTimeFormatted = LocalDateTime.ofInstant(
			Instant.ofEpochMilli(pseudoClock.getCurrentTime()), ZoneId.systemDefault()).format(DATE_TIME_FORMAT);        
        LOGGER.info( "\tCEP Engine PseudoClock current time: " + dateTimeFormatted);
		EntryPoint ep = kieSession.getEntryPoint(stream);

		// First insert the event
		FactHandle factHandle = ep.insert(cct);
		// And then advance the clock.
		LOGGER.info(" ");
        LOGGER.info("Inserting credit card [" + cct.getCreditCardNumber() + "] transaction [" + 
            cct.getTransactionNumber() + "] context into session.");
		dateTimeFormatted = LocalDateTime.ofInstant(
			Instant.ofEpochMilli(cct.getTimestamp()), ZoneId.systemDefault()).format(DATE_TIME_FORMAT);
		LOGGER.info( "\tCC Transaction Time: " + dateTimeFormatted);
        long advanceTime = cct.getTimestamp() - pseudoClock.getCurrentTime();
        
		if (advanceTime > 0) {
			long tSec = advanceTime/1000;
			LOGGER.info("\tAdvancing the PseudoClock with " + advanceTime + " milliseconds (" + tSec + "sec)" );
			
			pseudoClock.advanceTime(advanceTime, TimeUnit.MILLISECONDS);
			dateTimeFormatted = LocalDateTime.ofInstant(
				Instant.ofEpochMilli(pseudoClock.getCurrentTime()), ZoneId.systemDefault()).format(DATE_TIME_FORMAT);
			LOGGER.info( "\tCEP Engine PseudoClock ajusted time: " +  dateTimeFormatted);
		} else {
			// Print a warning when we don't need to advance the clock. This usually means that the events are entering the system in the
			// incorrect order.
            LOGGER.warn("Not advancing time. CreditCardTransaction timestamp is '" +
             cct.getTimestamp() + "', PseudoClock timestamp is '" + pseudoClock.getCurrentTime() + "'.");
        }
        
		return factHandle;
	}

    private String createCaseForPotentialFraud(PotentialFraudFact potentialFraudFact) {
        try {
            Map<String, Object> caseFile = new HashMap<>();
            caseFile.put("creditCardNumber", String.valueOf(potentialFraudFact.getCreditCardNumber()));
            caseFile.put("transactions", potentialFraudFact.getTransactions().toString());
            caseFile.put("investigationOrdered", "true");
            caseFile.put("transactionList", potentialFraudFact.getTransactions().toString());
            
            return baClient.createCase(processContainerId, processDefinitionId, caseFile);
        } catch (Exception e) {
            LOGGER.error("Business central not yet ready...");
            return null;
        }
    }

}
