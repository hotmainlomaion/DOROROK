// 도로록(DOROROK) — 프로젝트 루트 빌드 스크립트
// 실제 플러그인 버전은 gradle/libs.versions.toml 에서 관리한다.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
