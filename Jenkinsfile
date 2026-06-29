// ─────────────────────────────────────────────────────────────────────────────
// AI Test Generator — Jenkins Declarative Pipeline
//
// Pipeline strategy (smoke-first / fail-fast gate):
//
//   Build Docker Image
//        ↓
//   Smoke Gate — all 3 browsers in parallel (28/53 tests, ~5 min)
//        ↓ (abort pipeline if any browser fails)
//   Test — All Browsers — all 3 in parallel (53 tests, ~15 min)
//        ↓
//   Merge Results → Generate Allure Report
//
// Prerequisites (Jenkins plugins):
//   • Docker Pipeline       (docker.build, docker.image)
//   • Allure Jenkins Plugin (allure step)
//   • Email Extension       (emailext)
//
// Jenkins Credentials required (Manage Credentials → Global):
//   ID: anthropic-api-key      Kind: Secret text  (optional — FILE mode needs no key)
//   ID: saucedemo-login-pass   Kind: Secret text
//
// Trigger: automatically on SCM push; manually via "Build Now"
// ─────────────────────────────────────────────────────────────────────────────

pipeline {

    agent any

    options {
        timeout(time: 60, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '25', artifactNumToKeepStr: '10'))
        disableConcurrentBuilds(abortPrevious: true)
        timestamps()
    }

    environment {
        IMAGE_TAG   = "ai-test-generator:${BUILD_NUMBER}"
        MERGED_DIR  = "target/allure-results-merged"
    }

    // ── Stages ──────────────────────────────────────────────────────────────

    stages {

        stage('Build Docker Image') {
            steps {
                echo "Building image: ${IMAGE_TAG}"
                sh "docker build -t ${IMAGE_TAG} ."
            }
        }

        // ── Smoke gate — fast check, all 3 browsers in parallel ──────────────
        stage('Smoke Gate') {
            parallel {

                stage('Smoke — Chromium') {
                    steps {
                        withCredentials([
                            string(credentialsId: 'saucedemo-login-pass', variable: 'SAUCE_PASS'),
                            string(credentialsId: 'anthropic-api-key',    variable: 'ANTHROPIC_KEY')
                        ]) {
                            sh """
                                mkdir -p target/smoke-results-chromium
                                docker run --rm \\
                                  --name smoke-chromium-${BUILD_NUMBER} \\
                                  -e BROWSER=chromium \\
                                  -e HEADLESS=true \\
                                  -e SMOKE_ONLY=true \\
                                  -e LOGIN_PASS='${SAUCE_PASS}' \\
                                  -e ANTHROPIC_API_KEY='${ANTHROPIC_KEY}' \\
                                  -v ${WORKSPACE}/target/smoke-results-chromium:/app/target/allure-results \\
                                  ${IMAGE_TAG}
                            """
                        }
                    }
                }

                stage('Smoke — Firefox') {
                    steps {
                        withCredentials([
                            string(credentialsId: 'saucedemo-login-pass', variable: 'SAUCE_PASS'),
                            string(credentialsId: 'anthropic-api-key',    variable: 'ANTHROPIC_KEY')
                        ]) {
                            sh """
                                mkdir -p target/smoke-results-firefox
                                docker run --rm \\
                                  --name smoke-firefox-${BUILD_NUMBER} \\
                                  -e BROWSER=firefox \\
                                  -e HEADLESS=true \\
                                  -e SMOKE_ONLY=true \\
                                  -e LOGIN_PASS='${SAUCE_PASS}' \\
                                  -e ANTHROPIC_API_KEY='${ANTHROPIC_KEY}' \\
                                  -v ${WORKSPACE}/target/smoke-results-firefox:/app/target/allure-results \\
                                  ${IMAGE_TAG}
                            """
                        }
                    }
                }

                stage('Smoke — WebKit') {
                    steps {
                        withCredentials([
                            string(credentialsId: 'saucedemo-login-pass', variable: 'SAUCE_PASS'),
                            string(credentialsId: 'anthropic-api-key',    variable: 'ANTHROPIC_KEY')
                        ]) {
                            sh """
                                mkdir -p target/smoke-results-webkit
                                docker run --rm \\
                                  --name smoke-webkit-${BUILD_NUMBER} \\
                                  -e BROWSER=webkit \\
                                  -e HEADLESS=true \\
                                  -e SMOKE_ONLY=true \\
                                  -e LOGIN_PASS='${SAUCE_PASS}' \\
                                  -e ANTHROPIC_API_KEY='${ANTHROPIC_KEY}' \\
                                  -v ${WORKSPACE}/target/smoke-results-webkit:/app/target/allure-results \\
                                  ${IMAGE_TAG}
                            """
                        }
                    }
                }

            } // end smoke parallel
        }

        // ── Full suite — all 3 browsers in parallel (runs only if smoke passed) ──
        stage('Test — All Browsers') {
            parallel {

                stage('Chromium') {
                    steps {
                        withCredentials([
                            string(credentialsId: 'saucedemo-login-pass', variable: 'SAUCE_PASS'),
                            string(credentialsId: 'anthropic-api-key',    variable: 'ANTHROPIC_KEY')
                        ]) {
                            sh """
                                mkdir -p target/allure-results-chromium target/logs
                                docker run --rm \\
                                  --name test-chromium-${BUILD_NUMBER} \\
                                  -e BROWSER=chromium \\
                                  -e HEADLESS=true \\
                                  -e LOGIN_PASS='${SAUCE_PASS}' \\
                                  -e ANTHROPIC_API_KEY='${ANTHROPIC_KEY}' \\
                                  -v ${WORKSPACE}/target/allure-results-chromium:/app/target/allure-results \\
                                  -v ${WORKSPACE}/target/logs:/app/target/logs \\
                                  -v ${WORKSPACE}/test-history:/app/test-history \\
                                  ${IMAGE_TAG}
                            """
                        }
                    }
                }

                stage('Firefox') {
                    steps {
                        withCredentials([
                            string(credentialsId: 'saucedemo-login-pass', variable: 'SAUCE_PASS'),
                            string(credentialsId: 'anthropic-api-key',    variable: 'ANTHROPIC_KEY')
                        ]) {
                            sh """
                                mkdir -p target/allure-results-firefox
                                docker run --rm \\
                                  --name test-firefox-${BUILD_NUMBER} \\
                                  -e BROWSER=firefox \\
                                  -e HEADLESS=true \\
                                  -e LOGIN_PASS='${SAUCE_PASS}' \\
                                  -e ANTHROPIC_API_KEY='${ANTHROPIC_KEY}' \\
                                  -v ${WORKSPACE}/target/allure-results-firefox:/app/target/allure-results \\
                                  -v ${WORKSPACE}/target/logs:/app/target/logs \\
                                  -v ${WORKSPACE}/test-history:/app/test-history \\
                                  ${IMAGE_TAG}
                            """
                        }
                    }
                }

                stage('WebKit') {
                    steps {
                        withCredentials([
                            string(credentialsId: 'saucedemo-login-pass', variable: 'SAUCE_PASS'),
                            string(credentialsId: 'anthropic-api-key',    variable: 'ANTHROPIC_KEY')
                        ]) {
                            sh """
                                mkdir -p target/allure-results-webkit
                                docker run --rm \\
                                  --name test-webkit-${BUILD_NUMBER} \\
                                  -e BROWSER=webkit \\
                                  -e HEADLESS=true \\
                                  -e LOGIN_PASS='${SAUCE_PASS}' \\
                                  -e ANTHROPIC_API_KEY='${ANTHROPIC_KEY}' \\
                                  -v ${WORKSPACE}/target/allure-results-webkit:/app/target/allure-results \\
                                  -v ${WORKSPACE}/target/logs:/app/target/logs \\
                                  -v ${WORKSPACE}/test-history:/app/test-history \\
                                  ${IMAGE_TAG}
                            """
                        }
                    }
                }

            } // end full-suite parallel
        }

        stage('Merge Results') {
            steps {
                sh """
                    mkdir -p ${MERGED_DIR}
                    cp -rn target/allure-results-chromium/. ${MERGED_DIR}/ 2>/dev/null || true
                    cp -rn target/allure-results-firefox/.  ${MERGED_DIR}/ 2>/dev/null || true
                    cp -rn target/allure-results-webkit/.   ${MERGED_DIR}/ 2>/dev/null || true
                    echo "Merged result count: \$(ls ${MERGED_DIR} | wc -l)"
                """
            }
        }

        stage('Generate Allure Report') {
            steps {
                sh """
                    mvn allure:report \\
                      -Dallure.results.directory=${MERGED_DIR} \\
                      --no-transfer-progress -q
                """
            }
        }

    } // end stages

    // ── Post-build actions ───────────────────────────────────────────────────
    post {

        always {
            allure([
                results        : [[path: "${MERGED_DIR}"]],
                reportBuildPolicy: 'ALWAYS',
                includeProperties: true
            ])

            junit allowEmptyResults: true,
                  testResults: 'target/surefire-reports/**/*.xml'

            archiveArtifacts artifacts: 'target/allure-results-*/**',
                             allowEmptyArchive: true, fingerprint: false
            archiveArtifacts artifacts: 'target/smoke-results-*/**',
                             allowEmptyArchive: true, fingerprint: false
            archiveArtifacts artifacts: 'target/agent-reports/**',
                             allowEmptyArchive: true
            archiveArtifacts artifacts: 'target/logs/**',
                             allowEmptyArchive: true

            sh "docker rmi ${IMAGE_TAG} || true"
        }

        failure {
            emailext(
                to: '${DEFAULT_RECIPIENTS}',
                subject: "FAILED — ${JOB_NAME} #${BUILD_NUMBER}",
                body: """
                    <p>Build <b>${JOB_NAME} #${BUILD_NUMBER}</b> failed.</p>
                    <p>Console: <a href="${BUILD_URL}console">${BUILD_URL}console</a></p>
                    <p>Allure:  <a href="${BUILD_URL}allure">${BUILD_URL}allure</a></p>
                """,
                mimeType: 'text/html'
            )
        }

        fixed {
            emailext(
                to: '${DEFAULT_RECIPIENTS}',
                subject: "FIXED — ${JOB_NAME} #${BUILD_NUMBER}",
                body: "<p>Build is back to green: <a href='${BUILD_URL}'>${BUILD_URL}</a></p>",
                mimeType: 'text/html'
            )
        }

    } // end post

} // end pipeline