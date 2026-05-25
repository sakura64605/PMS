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
                    set +e
                    scp target/PMS-0.0.1-SNAPSHOT.jar ubuntu@YOUR_SERVER_IP:/opt/pms/pms.jar
                    ssh ubuntu@YOUR_SERVER_IP "sudo sed -i 's|ExecStart=.*|ExecStart=/usr/bin/java -Xms512m -Xmx1024m -jar /opt/pms/pms.jar --spring.profiles.active=prod --spring.redisson.config=file:/opt/pms/config/redisson.yml|' /etc/systemd/system/pms.service && sudo systemctl daemon-reload && sudo systemctl restart pms"
                    exit 0
                '''
            }
        }
    }
}