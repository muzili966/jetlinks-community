// Jenkinsfile — JetLinks 后端（jetlinks-community）
//
// 参考 wxxpay devops 流程编写，但 JetLinks 是单体应用，
// 不引入 Nacos / XXL-Job / 多服务依赖传播，故不依赖 wxxpay-ci Shared Library。
//
// 前置条件：
//   1. 构建节点需有 JDK17 + Maven + Docker（当前 Jenkins 只有内置节点且无标签，故用 agent any）
//   2. Credentials: harbor-jetlinks（Harbor jetlinks 项目的 robot 账号）、构建节点 ~/.ssh/config 已配 deploy-server 别名
//   3. 部署机已克隆本仓库到 ~/workspace/jetlinks-community，并在 docker/deploy/ 下
//      放好 .env.dev（由 .env.dev.example 复制后填真实口令，已 gitignore）

def SERVICE_NAME = 'jetlinks-api'
def REGISTRY      = '10.242.98.181:9093/jetlinks'
// 登录只需主机部分: 10.242.98.181:9093/jetlinks → 10.242.98.181:9093
def REGISTRY_HOST = '10.242.98.181:9093'
def DEPLOY_HOST  = '10.242.98.181'
def DEPLOY_DIR   = '~/workspace/jetlinks-community'  // 部署机上的仓库副本，compose 文件由 git 同步
// 各环境宿主机映射端口（容器内固定 8848；8848 被 Nacos 占用，故对外错开）
def API_PORT     = [dev: '8858', test: '8868']

pipeline {
    agent any

    // 与 wxxpay 各服务一致：使用 Jenkins 全局工具，不用 mvnw（避免每次构建重新下载 Maven）
    tools {
        maven 'maven-3.9'
        jdk 'jdk17'
    }

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
                // -s: 节点全局 settings 的 mirrorOf=* 会拦截 hsweb-nexus，导致 JetLinks
                //     SNAPSHOT 解析失败，故用仓库内 settings-ci.xml 放行（详见该文件注释）
                // -P '!ui': standalone 默认激活 ui profile，会把官方预编译的 UI 打进 jar，
                //     使后端 :8858 直接伺服一套【未含租户功能】的原版界面，与我们部署在
                //     :3200 的改造版并存、极易用错。后端只做 API，故禁用该 profile。
                // -DjacocoArgLine=: jacoco 的 argLine 占位符在非 verify 生命周期未填充，需显式置空
                sh """
                    mvn -B clean package \
                        -s .mvn/settings-ci.xml \
                        -P '!ui' \
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
                            export DOCKER_BUILDKIT=1
                            export DOCKER_MAX_CONCURRENT_UPLOADS=2
                            docker build -t ${env.IMAGE} -t ${REGISTRY}/${SERVICE_NAME}:latest .
                        """
                        // 显式登录：不依赖构建机手工 docker login 的会话（会过期，过期后一律 401）
                        withCredentials([usernamePassword(
                                credentialsId: 'harbor-jetlinks',
                                usernameVariable: 'HARBOR_USER',
                                passwordVariable: 'HARBOR_PASS')]) {
                            sh """
                                echo "\$HARBOR_PASS" | docker login ${REGISTRY_HOST} -u "\$HARBOR_USER" --password-stdin
                                docker push ${env.IMAGE}
                                docker push ${REGISTRY}/${SERVICE_NAME}:latest
                            """
                        }
                    }
                }
            }
        }

        stage('部署') {
            when { expression { params.ENV in ['dev', 'test', 'staging'] } }
            steps {
                // 与 wxxpay 一致：用构建节点 ~/.ssh/config 里的 deploy-server 别名，
                // 不依赖 SSH Agent 插件（该 Jenkins 未安装 ssh-agent plugin）。
                // compose 文件直接取自部署机上的仓库副本，git 同步后使用，无需手工拷贝；
                // .env.* 含口令、已 gitignore，只在部署机上维护一份，git reset 不会动它。
                sh """
                    ssh deploy-server "
                        cd ${DEPLOY_DIR} && \\
                        git fetch origin ${env.BRANCH_NAME} && \\
                        git reset --hard origin/${env.BRANCH_NAME} && \\
                        cd docker/deploy && \\
                        docker pull ${env.IMAGE} && \\
                        REGISTRY=${REGISTRY} TAG=${env.IMAGE_TAG} \\
                          docker compose -f docker-compose.${params.ENV}.yaml \\
                                         --env-file .env.${params.ENV} \\
                                         up -d --no-deps ${SERVICE_NAME}
                    "
                """
                echo "已部署: ${SERVICE_NAME} → ${params.ENV} (${env.IMAGE})"
            }
        }

        stage('部署验证') {
            when { expression { params.ENV in ['dev', 'test'] } }
            steps {
                // 禁用 ui profile 后根路径 / 返回 404（后端是纯 API），
                // 故用 actuator 健康端点探活；JetLinks 首次启动要建表，给足等待时间
                retry(10) {
                    sleep 15
                    sh "curl -sf -o /dev/null -w '%{http_code}' http://${DEPLOY_HOST}:${API_PORT[params.ENV]}/actuator/health | grep 200"
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
