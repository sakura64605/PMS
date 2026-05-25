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
                    scp target/PMS-0.0.1-SNAPSHOT.jar ubuntu@YOUR_SERVER_IP:/opt/pms/pms.jar
                    ssh ubuntu@YOUR_SERVER_IP "sudo systemctl restart pms"
                '''
            }
        }
    }
}