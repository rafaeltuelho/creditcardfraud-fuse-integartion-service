## Infrastructure services

To allow a quick setup of all services required to run this demo, we provide a docker compose template that starts the following services:
- Integration Service (Fuse)
- PHM Decision Service
- PHM Process Service
- Kafka Event Stream

This setup ensures that all services are connected using the default configuration.

In order to use it, please ensure you have Docker Compose installed on your machine, otherwise follow the instructions available
 in [here](https://docs.docker.com/compose/install/).
 
### Starting required services

  You should start all the services before you execute any of the Travel Agency applications, to do that please execute:
  
  For MacOS and Windows:
  
    docker-compose -f compose.yml up
  
  For Linux:
  
    docker-compose -f compose-linux.yml up
    
  Once all services bootstrap, the following ports will be assigned on your local machine:
  - Integration Service:
  - PHM Decision Service:
  - PHM Process Service:
  - Kafka: 9092
  
### Stopping and removing volume data
  
  To stop all services, simply run:

    docker-compose -f compose.yml stop
    
  It is also recommended to remove any of stopped containers by running:
  
    docker-compose -f compose.yml rm  
    
  For more details please check the Docker Compose documentation.
  
    docker-compose --help  
