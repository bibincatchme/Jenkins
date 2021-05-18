pipeline {
    agent {
        docker { image "docker.br.hmheng.io/base-ubuntu:16.04-openjdk8_181-builder" }
    }

    environment {
        HOME = "$WORKSPACE"
        app_name = 'my-app-name'
        docker_group = "my-docker-group"
    }

    options {
        skipStagesAfterUnstable()
    }
    stages {

        stage('Checkout') {
            steps {
                git credentialsId: 'myGitCredentiala', url: "git@my.repo.com:HMH/myrepo.git", branch: "$branch_name"
            }
        }

        stage('Package') {
            steps {
                sh "mvn clean package"
            }
        }

        stage('Generate Surefire Reports') {
            steps {
                sh 'mvn surefire-report:report'
            }
        }

        stage('SonarQube') {
            steps {
                script {
                    if (env.CHANGE_ID) {
                        if (env.CHANGE_BRANCH == "develop") {
                            env.BASE_BRANCH = "master"
                        } else {
                            env.BASE_BRANCH = "develop"
                        }

                        sh "mvn sonar:sonar -Dsonar.login=${sonar_token} \
                        -Dsonar.scm.revisions=${GIT_COMMIT} \
                        -Dsonar.pullrequest.key=${env.CHANGE_ID} \
                        -Dsonar.pullrequest.branch=${env.CHANGE_BRANCH} \
                        -Dsonar.pullrequest.base=${env.BASE_BRANCH}"
                    }
                    else {
                        sh "mvn sonar:sonar -Dsonar.login=${sonar_token}"
                    }
                }
            }
        }
    }
}
