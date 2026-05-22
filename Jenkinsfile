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
                    scp target/*.jar ubuntu@YOUR_SERVER_IP:/opt/pms/pms.jar
                    ssh ubuntu@YOUR_SERVER_IP "sudo systemctl restart pms"
                '''
            }
        }

        stage('重启服务') {
            steps {
                sh '''
                    ssh ubuntu@YOUR_SERVER_IP "pkill -f 'java.*pms.jar' || true"
                    ssh ubuntu@YOUR_SERVER_IP "cd /opt/pms && nohup java -jar pms.jar > logs/console.log 2>&1 &"
                '''
            }
        }
    }
}