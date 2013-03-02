//Use a custom plugins dir, because different branches use different plugin versions
grails.project.plugins.dir = "../local-plugins/risk-analytics-pc-cashflow-kti"

grails.project.dependency.resolution = {
    inherits "global" // inherit Grails' default dependencies
    log "warn"

    repositories {
        grailsHome()
        grailsCentral()
    }

    mavenRepo "https://repository.intuitive-collaboration.com/nexus/content/repositories/pillarone-public/"

    plugins {
        runtime ":background-thread:1.3"
        runtime ":hibernate:1.3.7"
        runtime ":joda-time:0.5"
        runtime ":maven-publisher:0.7.5"
        runtime ":quartz:0.4.2"
        runtime ":spring-security-core:1.1.2"
        runtime ":tomcat:1.3.7"

        test ":code-coverage:1.2.4"
        test ":excel-import:0.9.6"

        if (appName == "risk-analytics-pc-cashflow") {
            runtime "org.pillarone:risk-analytics-core:1.6-RC-4-kti"
            runtime("org.pillarone:risk-analytics-commons:0.4.27") { transitive = false }
        }
    }
}
//grails.plugin.location.'risk-analytics-core' = "../risk-analytics-core"
//grails.plugin.location.'risk-analytics-commons' = "../risk-analytics-commons"

grails.project.dependency.distribution = {
    String password = ""
    String user = ""
    String scpUrl = ""
    try {
        Properties properties = new Properties()
        properties.load(new File("${userHome}/deployInfo.properties").newInputStream())

        user = properties.get("user")
        password = properties.get("password")
        scpUrl = properties.get("url")
    } catch (Throwable t) {
    }
    remoteRepository(id: "pillarone", url: scpUrl) {
        authentication username: user, password: password
    }
}

coverage {
    exclusions = [
            'models/**',
            '**/*Test*',
            '**/com/energizedwork/grails/plugins/jodatime/**',
            '**/grails/util/**',
            '**/org/codehaus/**',
            '**/org/grails/**',
            '**GrailsPlugin**',
            '**TagLib**'
    ]

}