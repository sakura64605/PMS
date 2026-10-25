pipeline {
    agent any

    stages {
        stage('拉取代码') {
            steps {
                echo '代码已拉取'
            }
        }

        stage('Maven 打包') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('上传部署') {
            steps {
                sh '''
                    # Preserve live configuration on the server; uploaded jars contain no credentials.
                    SHA=$(sha256sum apps/pms-app/target/PMS-0.0.1-SNAPSHOT.jar | cut -d ' ' -f 1)
                    scp apps/pms-app/target/PMS-0.0.1-SNAPSHOT.jar ubuntu@111.229.254.252:/opt/pms/pms-upload.jar
                    scp scripts/deploy/promote_backend.py ubuntu@111.229.254.252:/opt/pms/promote_backend.py
                    ssh ubuntu@111.229.254.252 "python3 /opt/pms/promote_backend.py --incoming /opt/pms/pms-upload.jar --sha $SHA"
                '''
            }
        }
    }
}