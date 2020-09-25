.ONESHELL:
.SHELL := /bin/bash

args = `arg="$(filter-out $@,$(MAKECMDGOALS))" && echo $${arg:-${1}}`

TEST_SERVER_TAG=0.9

test:
	kubectl get nodes
	echo $(TEST_SERVER_TAG)

build-server:
	sh build-testserver.sh $(TEST_SERVER_TAG)

deploy-testserver:	
	cd test-server/dhall && TEST_SERVER_TAG=$(TEST_SERVER_TAG) dhall-to-yaml --documents < setup.dhall | kubectl create -n test -f -
undeploy-testserver:
	cd test-server/dhall && TEST_SERVER_TAG=$(TEST_SERVER_TAG) dhall-to-yaml --explain --documents < setup.dhall | kubectl delete -n test -f -

create-krb:
	kubectl create -f test-server/my-krb-1.yaml -n test