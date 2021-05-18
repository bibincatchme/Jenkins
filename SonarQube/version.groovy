node {
    String versionName = createVersion()
    stage('Build') {
        mvn ‘clean install -DskipTests -Drevision=${versionName}’
    }
    // ...
    stage('Deploy') {
        if (currentBuild.currentResult == 'SUCCESS') {
            if (env.BRANCH_NAME == ‘master’) {
                deployToKubernetes(versionName, 'kubeconfig-prod', 'hostname.com')
            } else if (env.BRANCH_NAME == 'develop') {
                deployToKubernetes(versionName, 'kubeconfig-staging', 'staging-hostname.com')
            }
        }
    }
}
String createVersion() {
    String versionName = ‘${new Date().format('yyyyMMddHHmm')}’

    if (env.BRANCH_NAME != ‘master’) {
        versionName += '-SNAPSHOT'
    }
    currentBuild.description = versionName
    return versionName
}
void deployToKubernetes(String versionName, String credentialsId, String hostname) {

    String DockerRegistry = 'your.Docker.registry.com'
    String imageName = “${DockerRegistry}/kitchensink:${versionName}"
    Docker.withRegistry("https://${DockerRegistry}", 'Docker-reg-credentials') {
        Docker.build(imageName, '.').push()
    }

    withCredentials([file(credentialsId: credentialsId, variable: 'kubeconfig')]) {
        withEnv(["IMAGE_NAME=${imageName}"]) {
            kubernetesDeploy(
                    credentialsType: 'KubeConfig',
                    kubeConfig: [path: kubeconfig],
                    configs: 'k8s/deployment.yaml',
                    enableConfigSubstitution: true
            )
        }
    }

    timeout(time: 2, unit: 'MINUTES') {
        waitUntil {
            sleep(time: 10, unit: 'SECONDS')
            isVersionDeployed(versionName, "http://${hostname}/rest/version")
        }
    }
}
boolean isVersionDeployed(String expectedVersion, String versionEndpoint) {
    def deployedVersion = sh(returnStdout: true, script: "curl -s ${versionEndpoint}").trim()
    return expectedVersion == deployedVersion
}
