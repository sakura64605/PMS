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
                    # 上传新 jar 并重启 systemd 服务（unit 文件已在服务器上配置好，无需改写 ExecStart）
                    scp target/PMS-0.0.1-SNAPSHOT.jar ubuntu@111.229.254.252:/opt/pms/pms.jar
                    ssh ubuntu@111.229.254.252 "sudo systemctl restart pms"
                '''
            }
        }
    }
}