// Jenkinsfile — JetLinks 后端（jetlinks-community）
//
// 参考 wxxpay devops 流程编写，但 JetLinks 是单体应用，
// 不引入 Nacos / XXL-Job / 多服务依赖传播，故不依赖 wxxpay-ci Shared Library。
//
// 前置条件：
//   1. Agent 需有 JDK17 + Maven + Docker（标签 java17-docker）
//   2. Credentials: deploy-ssh-key（部署服务器 SSH）
//   3. 部署服务器 /opt/jetlinks/compose/ 已放置 docker-compose.*.yaml 与 .env.*

def SERVICE_NAME = 'jetlinks-api'
def REGISTRY     = '10.242.98.181:9093/jetlinks'
def DEPLOY_HOST  = '10.242.98.181'
def DEPLOY_DIR   = '/opt/jetlinks/compose'
// 各环境宿主机映射端口（容器内固定 8848；8848 被 Nacos 占用，故对外错开）
def API_PORT     = [dev: '8858', test: '8868']

pipeline {
    agent { label 'java17-docker' }

    options {
        quietPeriod(10)
        disableConcurrentBuilds(abortPrevious: true)
        timeout(time: 45, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '30'))
        timestamps()
    }

    parameters {
        choice(name: 'ENV', choices: ['dev', 'test', 'staging', 'prod'], description: '部署目标环境')
        string(name: 'TAG', defaultValue: '', description: '镜像 Tag，留空则用 git short hash')
        booleanParam(name: 'SKIP_TESTS', defaultValue: false, description: '跳过单元测试（仅紧急发布使用）')
    }

    stages {
        stage('检出') {
            steps {
                script { env.TASK_START_TIME_MILLIS = "${System.currentTimeMillis()}" }
                checkout scm
            }
        }

        stage('Maven 构建') {
            steps {
                // jacoco 的 argLine 占位符在非 verify 生命周期未填充，需显式置空
                sh """
                    ./mvnw -B clean package \
                        -pl jetlinks-standalone -am \
                        ${params.SKIP_TESTS ? '-DskipTests' : ''} \
                        -DjacocoArgLine=
                """
            }
        }

        stage('单元测试报告') {
            when { expression { !params.SKIP_TESTS } }
            steps {
                junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
            }
        }

        stage('Docker 构建 & 推送') {
            steps {
                script {
                    def tag = params.TAG?.trim() ?: sh(
                        script: 'git rev-parse --short HEAD', returnStdout: true).trim()
                    env.IMAGE_TAG = tag
                    env.IMAGE = "${REGISTRY}/${SERVICE_NAME}:${tag}"

                    // Dockerfile 位于 jetlinks-standalone，构建上下文同目录（需 target/application.jar）
                    dir('jetlinks-standalone') {
                        sh """
                            docker build -t ${env.IMAGE} -t ${REGISTRY}/${SERVICE_NAME}:latest .
                            docker push ${env.IMAGE}
                            docker push ${REGISTRY}/${SERVICE_NAME}:latest
                        """
                    }
                }
            }
        }

        stage('部署') {
            when { expression { params.ENV in ['dev', 'test', 'staging'] } }
            steps {
                sshagent(credentials: ['deploy-ssh-key']) {
                    sh """
                        ssh -o StrictHostKeyChecking=no ${DEPLOY_HOST} "
                            cd ${DEPLOY_DIR} && \\
                            docker pull ${env.IMAGE} && \\
                            REGISTRY=${REGISTRY} TAG=${env.IMAGE_TAG} \\
                              docker compose -f docker-compose.${params.ENV}.yaml \\
                                             --env-file .env.${params.ENV} \\
                                             up -d --no-deps ${SERVICE_NAME}
                        "
                    """
                }
                echo "已部署: ${SERVICE_NAME} → ${params.ENV} (${env.IMAGE})"
            }
        }

        stage('部署验证') {
            when { expression { params.ENV in ['dev', 'test'] } }
            steps {
                // JetLinks 首次启动要建表，给足等待时间
                retry(10) {
                    sleep 15
                    sh "curl -sf -o /dev/null -w '%{http_code}' http://${DEPLOY_HOST}:${API_PORT[params.ENV]}/ | grep -E '200|302|401'"
                }
            }
        }
    }

    post {
        always { cleanWs() }
        failure {
            script {
                def taskStartMillis = (env.TASK_START_TIME_MILLIS ?: '0') as long
                def base = taskStartMillis > 0 ? taskStartMillis : currentBuild.startTimeInMillis
                def totalSec = ((System.currentTimeMillis() - base) / 1000).longValue()
                def durationStr = "${(totalSec / 60).intValue()}分${(totalSec % 60).intValue()}秒"
                echo "构建失败: ${SERVICE_NAME} [${params.ENV}] 分支=${env.BRANCH_NAME} 耗时=${durationStr}"
            }
        }
    }
}
