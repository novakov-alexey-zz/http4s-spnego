.ONESHELL:
.SHELL := /bin/bash

args = `arg="$(filter-out $@,$(MAKECMDGOALS))" && echo $${arg:-${1}}`

TEST_SERVER_TAG=0.9
NAMESPACE=test

test:
	kubectl get nodes
	echo $(TEST_SERVER_TAG)
	kubectl get ns ${NAMESPACE} || echo 'namespce ${NAMESPACE} does not exist. Please create it'

build-server:
	sh build-testserver.sh $(TEST_SERVER_TAG)

deploy-client-server:
	cd test-server/dhall && TEST_SERVER_TAG=$(TEST_SERVER_TAG) dhall-to-yaml --documents < setup.dhall | kubectl create -n ${NAMESPACE} -f -
undeploy-client-testserver:
	cd test-server/dhall && TEST_SERVER_TAG=$(TEST_SERVER_TAG) dhall-to-yaml --explain --documents < setup.dhall | kubectl delete -n ${NAMESPACE} -f -

deploy-krb-operator:
	wget -O- -q https://raw.githubusercontent.com/novakov-alexey/krb-operator/master/manifest/rbac.yaml | \
 		sed  -e "s:{{NAMESPACE}}:${NAMESPACE}:g" | kubectl create -n ${NAMESPACE} -f -
	kubectl create \
    	-f https://raw.githubusercontent.com/novakov-alexey/krb-operator/master/manifest/kube-deployment.yaml \
    	-n ${NAMESPACE}

undeploy-krb-operator:
	wget -O- -q https://raw.githubusercontent.com/novakov-alexey/krb-operator/master/manifest/rbac.yaml | \
    	sed  -e "s:{{NAMESPACE}}:${NAMESPACE}:g" | kubectl delete -n ${NAMESPACE} -f -
	
	kubectl delete -f https://raw.githubusercontent.com/novakov-alexey/krb-operator/master/manifest/kube-deployment.yaml -n ${NAMESPACE}
	kubectl delete crd krbs.io.github.novakov-alexey

create-principals:
	kubectl create -f test-server/my-krb-1.yaml -n ${NAMESPACE}