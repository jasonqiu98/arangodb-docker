# start the orchestration
start:
	docker compose -f docker-compose.yml up

# stop the orchestration
stop:
	docker compose stop

# restart the orchestration using a different .yml file
# -- because we want to use existing UUIDs stored in the nodes/ persisting volumes
# -- rather than let ArangoDB instances create their new ones
# don't use this if /logs and /nodes are already removed
restart:
	docker compose -f docker-compose-restart.yml up

# remove all the ArangoDB instance containers and relevant images
remove:
	docker compose down -v
	docker rmi arangodb:3.9.2
	docker rmi arangodb-docker-test:latest

# completely remove the persisting volumes
clean:
	sudo rm -rf logs nodes