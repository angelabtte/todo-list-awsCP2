pipeline {
    agent any

    stages {
        stage('Get Code') {
            steps {
                git branch: 'develop', url: 'https://github.com/angelabtte/todo-list-awsCP2.git'
            }
        }

        stage('Static Test') {
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
              environment {
               STAGE = 'production'  // Cambiar este valor según el entorno
              }
            steps {
                echo "Desplegando aplicación con AWS SAM en producción"
                              
                echo "Validando estructura del workspace"
                sh '''
                    ls -la
                    echo "Contenido de src"
                    ls -la src
                  '''

               // Construir y desplegar con SAM
               echo "El valor de Stage es: ${env.STAGE}"  // Para ver lo que Jenkins está pasando 
                sh """
                    sam build --template-file template.yaml
                    echo "El valor de Stage es: ${env.STAGE}"
                    sam deploy \
                        --stack-name staging-todolistAWS3 \
                        --s3-bucket todo-sam-staging-angelabtte-2025 \
                        --no-confirm-changeset \
                        --capabilities CAPABILITY_IAM \
                        --no-fail-on-empty-changeset

               """
            }
        }
             stage('Rest Test') {
                steps {
                    echo "Ejecutando pruebas de integración REST con pytest en producción"
                    sh '''
                        pip install pytest requests
            
                        echo "Obteniendo la URL base de la API desplegada..."
                        BASE_URL=$(aws cloudformation describe-stacks --stack-name staging-todolistAWS3 \
                            --query "Stacks[0].Outputs[?OutputKey=='BaseUrlApi'].OutputValue" --output text)
            
                        echo "URL pública detectada: $BASE_URL"
            
                        echo "Ejecutando pytest..."
                        BASE_URL=$BASE_URL PATH=$PATH:~/.local/bin pytest test/integration/todoApiTest.py \
                            --maxfail=1 --disable-warnings -q \
                            --junitxml=rest-test-report.xml
                    '''
                }
                post {
                    always {
                        junit 'rest-test-report.xml'
                    }
                }
            }
                stage('Promote') {
            steps {
                echo "Promoviendo versión a producción: merge de 'develop' a 'master'"

                sh '''
                    git config --global user.email "angel.bts12@gmail.com"
                    git config --global user.name "angelabtte"
                '''

                withCredentials([usernamePassword(credentialsId: 'tokencp3', usernameVariable: 'angelabtte', passwordVariable: 'tokencp3')]) {
                    sh '''
                        cd todo-list-awsCP2
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
}