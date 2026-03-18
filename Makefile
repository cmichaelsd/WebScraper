NAMESPACE    := webscraper
API_IMAGE    := webscraper/api:local
WORKER_IMAGE := webscraper/worker:local
AWS_REGION   := us-west-1
INFRA_DIR    := WebScraperInfra

.PHONY: k8s-start k8s-build k8s-deploy k8s-up k8s-down k8s-status \
        k8s-logs-api k8s-logs-worker k8s-url k8s-restart \
        k8s-aws-auth k8s-aws-setup k8s-aws-deploy k8s-aws-status \
        k8s-aws-logs-api k8s-aws-logs-worker k8s-aws-url k8s-aws-down

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

# ── AWS / EKS ────────────────────────────────────────────────────────────────

## Configure kubectl to point at the EKS cluster
k8s-aws-auth:
	aws eks update-kubeconfig --region $(AWS_REGION) --name $(NAMESPACE)

## Create namespace, service account (with IRSA annotation), and DB secret
k8s-aws-setup: k8s-aws-auth
	kubectl apply -f k8s/local/namespace.yaml
	kubectl create serviceaccount webscraper-sa -n $(NAMESPACE) --dry-run=client -o yaml \
	  | kubectl apply -f -
	kubectl annotate serviceaccount webscraper-sa -n $(NAMESPACE) \
	  eks.amazonaws.com/role-arn=$$(terraform -chdir=$(INFRA_DIR) output -raw pod_role_arn) \
	  --overwrite
	kubectl create secret generic db-credentials -n $(NAMESPACE) \
	  --from-literal=DATABASE_URL=$$(terraform -chdir=$(INFRA_DIR) output -raw api_database_url) \
	  --from-literal=JDBC_URL=$$(terraform -chdir=$(INFRA_DIR) output -raw jdbc_url) \
	  --from-literal=DB_USER=$$(terraform -chdir=$(INFRA_DIR) output -raw db_username) \
	  --from-literal=DB_PASSWORD=$$(terraform -chdir=$(INFRA_DIR) output -raw db_password) \
	  --dry-run=client -o yaml | kubectl apply -f -

## Deploy API and worker to EKS
k8s-aws-deploy: k8s-aws-setup
	kubectl apply -f k8s/aws/api.yaml
	kubectl apply -f k8s/aws/worker.yaml

## Show all resources in the EKS webscraper namespace
k8s-aws-status:
	kubectl get all -n $(NAMESPACE)

## Stream API logs from EKS
k8s-aws-logs-api:
	kubectl logs -n $(NAMESPACE) -l app=webscraper-api -f

## Stream worker logs from EKS
k8s-aws-logs-worker:
	kubectl logs -n $(NAMESPACE) -l app=webscraper-worker -f

## Print the NLB hostname for the API
k8s-aws-url:
	@kubectl get svc webscraper-api -n $(NAMESPACE) \
	  -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'
	@echo ""

## Tear down all app resources from EKS (leaves cluster intact)
k8s-aws-down:
	kubectl delete namespace $(NAMESPACE) --ignore-not-found
