pipeline {
    agent any
    tools {
        ansible 'ansible'
        terraform 'terraform'
    }
    environment {
        AWS_REGION = "us-east-1"
        AWS_ACCOUNT_ID = sh(script: '...', returnStdout: true).trim()
        ECR_REGISTRY = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
        APP_REPO_NAME = "your-app-repo/your-app-name"
    }
    stages {
        stage('Create Infrastructure for the App') {
            // Use Terraform to create the necessary infrastructure on AWS
            sh 'terraform init'
            sh 'terraform apply -auto-approve'
        }
        stage('Create ECR Repo') {
            // Create an ECR repository to store Docker images
            sh 'aws ecr create-repository --repository-name ${APP_REPO_NAME}'
        }
        stage('Build App Docker Image') {
            // Build Docker images for the application components
            sh 'docker build -t ${APP_REPO_NAME} .'
        }
        stage('Push Image to ECR Repo') {
            // Push the Docker images to the ECR repository
            sh 'docker tag ${APP_REPO_NAME} ${ECR_REGISTRY}/${APP_REPO_NAME}'
            sh 'docker push ${ECR_REGISTRY}/${APP_REPO_NAME}'
        }
        stage('Wait for Instance') {
            // Wait for the EC2 instance hosting the application to be in a stable running state
            sh 'aws ec2 wait instance-status-ok --instance-ids ${INSTANCE_ID}'
        }
        stage('Deploy the App') {
            // Deploy the Dockerized application using Ansible
            sh 'ansible-playbook -i ${INSTANCE_ID} deploy.yml'
        }
        stage('Destroy the infrastructure') {
            // Destroy the infrastructure once the deployment is completed
            sh 'terraform destroy --auto-approve'
        }
    }
    post {
        always {
            echo 'Deleting all local images'
            sh 'docker image prune -af'
        }
        failure {
            echo 'Delete the Image Repository on ECR due to the Failure'
            sh 'aws ecr delete-repository --repository-name ${APP_REPO_NAME}'
            echo 'Deleting Terraform Stack due to the Failure'
            sh 'terraform destroy --auto-approve'
        }
    }
}
