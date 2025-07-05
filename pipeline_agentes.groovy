pipeline {
    agent none

    stages {
        stage('Get Code') {
            agent { label 'agent3' }
            steps {
                sh 'echo "=== Etapa: Get Code ==="; whoami; hostname'
                dir('todo-list-awsCP2') {
                    git branch: 'develop', url: 'https://github.com/angelabtte/todo-list-awsCP2.git'
                }
                stash name: 'source-code', includes: 'todo-list-awsCP2/**/*'
            }
        }

        stage('Static Test') {
            agent { label 'agent1' }
            steps {
                sh 'echo "=== Etapa: Static Test ==="; whoami; hostname'
                unstash 'source-code'
                dir('todo-list-awsCP2/src') {
                    sh 'flake8 . --output-file=flake8-report.txt || true'
                    sh 'bandit -r . -f txt -o bandit-report.txt || true'
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'todo-list-awsCP2/src/flake8-report.txt, todo-list-awsCP2/src/bandit-report.txt', allowEmptyArchive: true
                }
            }
        }

        stage('Deploy') {
            agent { label 'agent3' }
            environment {
                STAGE = 'production'
            }
            steps {
                sh 'echo "=== Etapa: Deploy (Staging) ==="; whoami; hostname'
                unstash 'source-code'
                dir('todo-list-awsCP2') {
                    sh '''
                        sam build --template-file template.yaml
                        sam deploy \
                            --stack-name staging-todolistAWS3 \
                            --s3-bucket todo-sam-staging-angelabtte-2025 \
                            --no-confirm-changeset \
                            --capabilities CAPABILITY_IAM \
                            --no-fail-on-empty-changeset

                        BASE_URL=$(aws cloudformation describe-stacks --stack-name staging-todolistAWS3 \
                            --query "Stacks[0].Outputs[?OutputKey=='BaseUrlApi'].OutputValue" --output text)

                        echo $BASE_URL > api_url.txt
                    '''
                }
                stash name: 'api-url', includes: 'todo-list-awsCP2/api_url.txt'
            }
        }

        stage('Rest Test') {
            agent { label 'agent2' }
            steps {
                sh 'echo "=== Etapa: Rest Test (Staging) ==="; whoami; hostname'
                unstash 'source-code'
                unstash 'api-url'
                dir('todo-list-awsCP2') {
                    sh '''
                        pip install pytest requests
                        BASE_URL=$(cat api_url.txt)
                        echo "URL pública detectada: $BASE_URL"
                        BASE_URL=$BASE_URL PATH=$PATH:~/.local/bin pytest test/integration/todoApiTest.py \
                            --maxfail=1 --disable-warnings -q \
                            --junitxml=rest-test-report.xml
                    '''
                }
            }
            post {
                always {
                    junit 'todo-list-awsCP2/rest-test-report.xml'
                }
            }
        }

        stage('Promote') {
            agent { label 'agent3' }
            steps {
                sh 'echo "=== Etapa: Promote ==="; whoami; hostname'
                unstash 'source-code'
                dir('todo-list-awsCP2') {
                    sh '''
                        git config --global user.email "angel.bts12@gmail.com"
                        git config --global user.name "angelabtte"
                    '''
                    withCredentials([usernamePassword(credentialsId: 'tokencp3', usernameVariable: 'angelabtte', passwordVariable: 'tokencp3')]) {
                        sh '''
                            git remote set-url origin https://${angelabtte}:${tokencp3}@github.com/angelabtte/todo-list-awsCP2.git
                            git fetch origin
                            git checkout master || git checkout -b master origin/develop
                            git merge origin/develop -m "Promoción automática a producción desde Jenkins"
                            git push origin master
                        '''
                    }
                }
            }
        }

        stage('CD - Deploy to Production') {
            agent { label 'agent3' }
            environment {
                STAGE = 'production'
            }
            steps {
                sh 'echo "=== Etapa: CD - Deploy to Production ==="; whoami; hostname'
                dir('todo-list-awsCP2') {
                    sh '''
                        sam build --template-file template.yaml
                        sam deploy \
                            --stack-name prod-todolistAWS4 \
                            --s3-bucket todo-sam-prod-angelabtte-2025 \
                            --no-confirm-changeset \
                            --capabilities CAPABILITY_IAM \
                            --no-fail-on-empty-changeset

                        BASE_URL=$(aws cloudformation describe-stacks --stack-name prod-todolistAWS4 \
                            --query "Stacks[0].Outputs[?OutputKey=='BaseUrlApi'].OutputValue" --output text)

                        echo $BASE_URL > prod_api_url.txt
                    '''
                }
                stash name: 'prod-api-url', includes: 'todo-list-awsCP2/prod_api_url.txt'
            }
        }

        stage('CD - Readonly Tests') {
            agent { label 'agent2' }
            steps {
                sh 'echo "=== Etapa: CD - Readonly Tests ==="; whoami; hostname'
                unstash 'source-code'
                unstash 'prod-api-url'
                dir('todo-list-awsCP2') {
                    sh '''
                        pip install --user pytest requests
                        BASE_URL=$(cat prod_api_url.txt)
                        echo "URL pública detectada: $BASE_URL"
                        BASE_URL=$BASE_URL PATH=$PATH:~/.local/bin pytest test/integration/todoApiTest_modified.py \
                            -m "readonly" \
                            --maxfail=1 --disable-warnings -q \
                            --junitxml=readonly-test-report.xml
                    '''
                }
            }
            post {
                always {
                    junit 'todo-list-awsCP2/readonly-test-report.xml'
                }
            }
        }
    }
}
