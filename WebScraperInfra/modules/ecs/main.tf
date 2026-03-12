locals {
  asyncpg_url = "postgresql+asyncpg://${var.db_username}:${var.db_password}@${var.db_endpoint}:${var.db_port}/${var.db_name}"
  jdbc_url    = "jdbc:postgresql://${var.db_endpoint}:${var.db_port}/${var.db_name}"
}

resource "aws_ecs_cluster" "this" {
  name = var.project_name
}

resource "aws_iam_role" "ecs_task_execution_role" {
  name = "${var.project_name}-ecs-task-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_execution_policy" {
  role       = aws_iam_role.ecs_task_execution_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_cloudwatch_log_group" "api" {
  name              = "/ecs/${var.project_name}-api"
  retention_in_days = 7
}

resource "aws_cloudwatch_log_group" "worker" {
  name              = "/ecs/${var.project_name}-worker"
  retention_in_days = 7
}

resource "aws_secretsmanager_secret" "api_database_url" {
  name = "${var.project_name}-api-database-url"
}

resource "aws_secretsmanager_secret_version" "api_database_url" {
  secret_id     = aws_secretsmanager_secret.api_database_url.id
  secret_string = local.asyncpg_url
}

resource "aws_iam_role_policy" "secrets_access" {
  name = "${var.project_name}-secrets-access"
  role = aws_iam_role.ecs_task_execution_role.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = "secretsmanager:GetSecretValue"
      Resource = [
        var.db_secret_arn,
        aws_secretsmanager_secret.api_database_url.arn
      ]
    }]
  })
}

resource "aws_ecs_task_definition" "api" {
  family                   = "${var.project_name}-api"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "256"
  memory                   = "512"
  execution_role_arn       = aws_iam_role.ecs_task_execution_role.arn

  container_definitions = jsonencode([
    {
      name      = "api"
      image     = var.api_image
      essential = true

      portMappings = [
        {
          containerPort = 8000
          protocol      = "tcp"
        }
      ]

      secrets = [
        {
          name      = "DATABASE_URL"
          valueFrom = aws_secretsmanager_secret.api_database_url.arn
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.api.name
          awslogs-region        = var.region
          awslogs-stream-prefix = "ecs"
        }
      }
    }
  ])
}

resource "aws_ecs_task_definition" "worker" {
  family                   = "${var.project_name}-worker"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "256"
  memory                   = "512"
  execution_role_arn       = aws_iam_role.ecs_task_execution_role.arn

  container_definitions = jsonencode([
    {
      name      = "worker"
      image     = var.worker_image
      essential = true

      environment = [
        {
          name  = "JDBC_URL"
          value = local.jdbc_url
        }
        # {
        #   name  = "WORKER_ID"
        #   value = "worker-1"
        # }
      ]

      secrets = [
        {
          name      = "DB_USER"
          valueFrom = "${var.db_secret_arn}:username::"
        },
        {
          name      = "DB_PASSWORD"
          valueFrom = "${var.db_secret_arn}:password::"
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.worker.name
          awslogs-region        = var.region
          awslogs-stream-prefix = "ecs"
        }
      }
    }
  ])
}

resource "aws_ecs_service" "api" {
  name            = "${var.project_name}-api"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.api.arn
  launch_type     = "FARGATE"
  desired_count   = 1

  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [var.api_sg_id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = var.target_group_arn
    container_name   = "api"
    container_port   = 8000
  }
}

resource "aws_ecs_service" "worker" {
  name            = "${var.project_name}-worker"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.worker.arn
  launch_type     = "FARGATE"
  desired_count   = 1

  network_configuration {
    subnets          = var.private_subnet_ids
    security_groups  = [var.worker_sg_id]
    assign_public_ip = false
  }
}