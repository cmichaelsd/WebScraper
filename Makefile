NAMESPACE   := webscraper
API_IMAGE   := webscraper/api:local
WORKER_IMAGE := webscraper/worker:local

.PHONY: k8s-start k8s-build k8s-deploy k8s-up k8s-down k8s-status \
        k8s-logs-api k8s-logs-worker k8s-url k8s-restart

## Start minikube (docker driver)
k8s-start:
	minikube start --driver=docker

## Build both images directly into minikube's Docker daemon
k8s-build:
	minikube image build -t $(API_IMAGE) ./WebScraperAPI
	minikube image build -t $(WORKER_IMAGE) ./WebScraperWorker

## Apply all manifests (postgres first, wait for ready, then api + worker)
k8s-deploy:
	kubectl apply -f k8s/local/namespace.yaml
	kubectl apply -f k8s/local/secret.yaml
	kubectl apply -f k8s/local/postgres.yaml
	@echo "Waiting for postgres to be ready..."
	kubectl wait --for=condition=ready pod -l app=postgres -n $(NAMESPACE) --timeout=90s
	kubectl apply -f k8s/local/api.yaml
	kubectl apply -f k8s/local/worker.yaml

## Full start: minikube + build + deploy
k8s-up: k8s-start k8s-build k8s-deploy
	@echo ""
	@echo "Cluster is up. API available at:"
	@minikube service webscraper-api -n $(NAMESPACE) --url

## Rebuild images and restart both deployments (no cluster teardown)
k8s-restart:
	$(MAKE) k8s-build
	kubectl rollout restart deployment/webscraper-api -n $(NAMESPACE)
	kubectl rollout restart deployment/webscraper-worker -n $(NAMESPACE)

## Tear down everything (deletes the namespace and all resources inside it)
k8s-down:
	kubectl delete namespace $(NAMESPACE) --ignore-not-found

## Show all resources in the webscraper namespace
k8s-status:
	kubectl get all -n $(NAMESPACE)

## Stream API logs
k8s-logs-api:
	kubectl logs -n $(NAMESPACE) -l app=webscraper-api -f --all-containers

## Stream worker logs
k8s-logs-worker:
	kubectl logs -n $(NAMESPACE) -l app=webscraper-worker -f

## Print the minikube URL for the API
k8s-url:
	@minikube service webscraper-api -n $(NAMESPACE) --url
