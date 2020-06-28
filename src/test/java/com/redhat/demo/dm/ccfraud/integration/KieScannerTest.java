package com.redhat.demo.dm.ccfraud.integration;

import org.drools.compiler.kproject.ReleaseIdImpl;
import org.junit.Test;
import org.kie.api.KieServices;
import org.kie.api.builder.KieScanner;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

public class KieScannerTest {

	final static String G = "com.redhat.demo.dm";
	final static String A = "creditcardfraud-rules";
	final static String V = "2.0.0";

	private KieSession kSession;
	private KieServices kieServices;
	private ReleaseIdImpl releaseId;
	private KieContainer kContainer;
	private KieScanner kScanner;

	@Test
	public void fireRules() throws InterruptedException {
		kieServices = KieServices.Factory.get();
		releaseId = new ReleaseIdImpl(G, A, V);
		kContainer = kieServices.newKieContainer(releaseId);
		kScanner = kieServices.newKieScanner(kContainer);
		kSession = kContainer.newKieSession();
		kScanner.start(3000);
		while (true) {
			Thread.sleep(3000);
			kSession.fireAllRules();
		}
	}
}