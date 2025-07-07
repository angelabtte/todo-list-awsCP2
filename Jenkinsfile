pipeline {
    agent any

    environment {
        STAGE = ''
    }

    stages {
        stage('Get Code') {
            steps {
                script {
                    def branch = env.BRANCH_NAME
                    echo "Rama detectada: ${branch}"

                    if (branch == 'develop') {
                        STAGE = 'staging'
                        echo "Clonando código fuente desde develop..."
                        git branch: 'develop', url: 'https://github.com/angelabtte/todo-list-awsCP2.git'

                        echo "Descargando configuración desde staging..."
                        sh '''
                            curl -L https://raw.githubusercontent.com/angelabtte/todo-list-aws-config-CPD/staging/samconfig.toml -o samconfig.toml
                            cat samconfig.toml
                        '''
                    } else if (branch == 'master') {
                        STAGE = 'production'
                        echo "Clonando código fuente desde master..."
                        git branch: 'master', url: 'https://github.com/angelabtte/todo-list-awsCP2.git'

                        echo "Descargando configuración desde production..."
                        sh '''
                            curl -L https://raw.githubusercontent.com/angelabtte/todo-list-aws-config-CPD/production/samconfig.toml -o samconfig.toml
                            cat samconfig.toml
                        '''
                    } else {
                        error "Rama no soportada: ${branch}"
                    }
                }
            }
        }

        stage('Static Test') {
            when {
                expression { env.BRANCH_NAME == 'develop' }
            }
            steps {
                dir('src') {
                    sh 'flake8 . --output-file=flake8-report.txt || true'
                    sh 'bandit -r . -f txt -o bandit-report.txt || true'
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'src/flake8-report.txt, src/bandit-report.txt', allowEmptyArchive: true
                }
            }
        }

        stage('Deploy') {
            steps {
                echo "Desplegando aplicación con AWS SAM en entorno ${STAGE}..."
                sh '''
                    echo "Estructura del workspace:"
                    ls -la
                    echo "Contenido de src:"
                    ls -la src
                '''
                sh '''
                    sam build --template-file template.yaml
                    sam deploy || true
                '''
            }
        }

        stage('Rest Test') {
            steps {
                script {
                    if (env.BRANCH_NAME == 'develop') {
                        echo "Pruebas de integración en staging..."
                        sh '''
                            pip install pytest requests
                            BASE_URL=$(aws cloudformation describe-stacks --stack-name staging-todolistAWS3                                 --query "Stacks[0].Outputs[?OutputKey=='BaseUrlApi'].OutputValue" --output text)
                            echo "URL pública: $BASE_URL"
                            BASE_URL=$BASE_URL PATH=$PATH:~/.local/bin pytest test/integration/todoApiTest.py                                 --maxfail=1 --disable-warnings -q                                 --junitxml=rest-test-report.xml
                        '''
                    } else if (env.BRANCH_NAME == 'master') {
                        echo "Pruebas de solo lectura en producción..."
                        sh '''
                            pip install --user pytest requests
                            BASE_URL=$(aws cloudformation describe-stacks --stack-name prod-todolistAWS4                                 --query 'Stacks[0].Outputs[?OutputKey==`BaseUrlApi`].OutputValue' --output text)
                            echo "URL pública: $BASE_URL"
                            BASE_URL=$BASE_URL PATH=$PATH:~/.local/bin pytest test/integration/todoApiTest_modified.py                                 -m "readonly"                                 --maxfail=1 --disable-warnings -q                                 --junitxml=readonly-test-report.xml
                        '''
                    }
                }
            }
            post {
                always {
                    script {
                        if (env.BRANCH_NAME == 'develop') {
                            junit 'rest-test-report.xml'
                        } else if (env.BRANCH_NAME == 'master') {
                            junit 'readonly-test-report.xml'
                        }
                    }
                }
            }
        }

        stage('Promote') {
    when {
        expression { env.BRANCH_NAME == 'develop' }
    }
    steps {
        echo "Promoviendo versión a producción..."
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
                git pull --rebase origin master
                git push origin master
            '''
        }
    }
}

    }
}
