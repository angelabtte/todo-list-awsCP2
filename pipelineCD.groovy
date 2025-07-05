pipeline {
    agent any

    environment {
        STAGE = 'production'
    }

    stages {
        stage('Get Code') {
            steps {
                git branch: 'master', url: 'https://github.com/angelabtte/todo-list-awsCP2.git'
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
                        --stack-name prod-todolistAWS4 \
                        --s3-bucket todo-sam-prod-angelabtte-2025 \
                        --no-confirm-changeset \
                        --capabilities CAPABILITY_IAM \
                        --no-fail-on-empty-changeset

               """
            }
        }
        stage('Rest Test') {
            steps {
                echo "Ejecutando pruebas de solo lectura en producción"
        
                sh '''
                    pip install --user pytest requests
        
                    BASE_URL=$(aws cloudformation describe-stacks --stack-name prod-todolistAWS4 \
                        --query "Stacks[0].Outputs[?OutputKey=='BaseUrlApi'].OutputValue" --output text)
        
                    echo "URL pública detectada: $BASE_URL"
        
                    # Ejecutar solo pruebas marcadas como 'readonly'
                    BASE_URL=$BASE_URL PATH=$PATH:~/.local/bin pytest test/integration/todoApiTest_modified.py \
                        -m "readonly" \
                        --maxfail=1 --disable-warnings -q \
                        --junitxml=readonly-test-report.xml
                '''
            }
            post {
                always {
                    junit 'readonly-test-report.xml'
                }
            }
        }
    }
}
