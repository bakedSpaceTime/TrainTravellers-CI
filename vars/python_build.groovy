def call(dockerRepoName, imageName, portNum) {
    pipeline {
    agent any
        parameters {
            booleanParam(defaultValue: false, description: 'Deploy the App', name: 'DEPLOY')
        }
        stages {
        stage('Build') {
            steps {
            sh 'pip install -r requirements.txt'
            }
        }
        stage('Python Lint') {
            steps {
            sh 'pylint-fail-under --fail_under 5 *.py'
            }
        }
        stage('Unit Test') {
            steps {
            script{
                def filelist = findFiles(glob: 'test*.py')
                for (int i = 0; i < filelist.size(); i++) {
                def filename = filelist[i]
                sh "coverage run --omit */site-packages/*,*/dist-packages/* ${filename}"
                }
            }
            }
            post {
            always {
                sh 'coverage report'
                script {
                    def test_reports_exist = fileExists 'test-reports'
                    if (test_reports_exist) {
                        junit 'test-reports/*.xml'
                    }
                }
            }
            }
        }
        stage('Package') {
            when {
                expression {
                    env.GIT_BRANCH == 'origin/main'
                }
            }
            steps {
                withCredentials([string(credentialsId: 'DockerHub', variable: 'TOKEN')]) {
                    sh "docker login -u 'ncrooks' -p '$TOKEN' docker.io"
                    sh "docker build -t ${dockerRepoName}:latest --tag ncrooks/${dockerRepoName}:${imageName} ."
                    sh "docker push ncrooks/${dockerRepoName}:${imageName}"
                }
            }
        }
        stage('Zip Artifacts') {
            steps {
                sh 'zip app.zip *.py'
            }
            post {
            always {
                archiveArtifacts artifacts: 'app.zip'
            }
            }
        }
        stage('Deliver') {
            when {
               expression { params.DEPLOY }
            }
            steps {
                sh "docker stop ${dockerRepoName} || true && docker rm ${dockerRepoName} || true"
                sh "docker run -d -p ${portNum}:${portNum} --name ${dockerRepoName} ${dockerRepoName}:latest"
            }
        }
        }
    }
}
