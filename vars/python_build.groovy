def call(buildDir, dockerRepoName, tagName, portNum) {
    pipeline {
    agent any
        parameters {
            booleanParam(defaultValue: false, description: 'Deploy the App', name: 'DEPLOY')
        }
        stages {
        stage('Build') {
            steps {
            sh "pip install -r ./${buildDir}/requirements.txt"
            }
        }
        stage('Python Lint') {
            steps {
            sh "pylint-fail-under --fail_under 5 ./${buildDir}/*.py"
            }
        }
        stage('Package') {
            when {
                expression {
                    env.GIT_BRANCH == 'origin/main'
                }
            }
            steps {
                withCredentials([string(credentialsId: '5b19aa88-663b-4267-8695-1c88fcf30492', variable: 'TOKEN')]) {
                    sh "docker login -u 'bakedspacetime' -p '$TOKEN' docker.io"
                    sh "docker build -t ${dockerRepoName}:latest --tag bakedspacetime/${dockerRepoName}:${tagName} ./${buildDir}"
                    sh "docker push bakedspacetime/${dockerRepoName}:${tagName}"
                }
            }
        }
        stage('Zip Artifacts') {
            steps {
                sh "zip ${tagName}_app.zip ./${buildDir}/*.py"
            }
            post {
            always {
                archiveArtifacts artifacts: "${tagName}_app.zip"
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
