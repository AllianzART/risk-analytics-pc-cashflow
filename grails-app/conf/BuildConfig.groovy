//Use a custom plugins dir, because different branches use different plugin versions
grails.project.plugins.dir = "../local-plugins/risk-analytics-pc-cashflow-release-1.10"

grails.project.dependency.resolver = "maven"

grails.project.dependency.resolution = {
    inherits "global" // inherit Grails' default dependencies
    log "warn"

    repositories {
        grailsHome()
        mavenLocal()

        mavenRepo (name:"pillarone" , url:"http://zh-artisan-test.art-allianz.com:8085/nexus/content/groups/public/") {
            updatePolicy System.getProperty('snapshotUpdatePolicy') ?: 'daily'
        }
        grailsCentral()
        mavenCentral()
        mavenRepo 'http://repo.spring.io/milestone'
    }

    plugins {
        runtime ":background-thread:1.3"
        runtime ":hibernate:3.6.10.3"
        runtime ":release:3.0.1"
        runtime ":quartz:1.0.1"
        runtime ":spring-security-core:2.0-RC2"
        runtime ":tomcat:7.0.42"

        test ":code-coverage:1.2.7"
        compile ":excel-import:1.0.0"

        if (appName == "risk-analytics-pc-cashflow") {
            runtime "org.pillarone:risk-analytics-core:1.10.2-SNAPSHOT"
            runtime("org.pillarone:risk-analytics-commons:1.10.2-SNAPSHOT") { transitive = false }
        }
    }

    dependencies {
        test 'hsqldb:hsqldb:1.8.0.10'
        compile (group:'org.apache.poi', name:'poi', version:'3.9');
        compile (group:'org.apache.poi', name:'poi-ooxml', version:'3.9') {
            excludes 'xmlbeans'
        }
    }
}
//grails.plugin.location.'risk-analytics-core' = "../risk-analytics-core-release-1.10"
//grails.plugin.location.'risk-analytics-commons' = "../risk-analytics-commons-release-1.10"

grails.project.repos.default = "pillarone"

grails.project.dependency.distribution = {
    String password = ""
    String user = ""
    String scpUrl = ""
    try {
        Properties properties = new Properties()
        String version = new GroovyClassLoader().loadClass('RiskAnalyticsPcCashflowGrailsPlugin').newInstance().version
        properties.load(new File("${userHome}/deployInfo.properties").newInputStream())
        user = properties.get("user")
        password = properties.get("password")

        if (version?.endsWith('-SNAPSHOT')){
            scpUrl = properties.get("urlSnapshot")
        }else {
            scpUrl = properties.get("url")
        }
	remoteRepository(id: "pillarone", url: scpUrl) {
        	authentication username: user, password: password
    	}

    } catch (Throwable t) {
        println "Error (deployInfo.properties not found in userhome?): ${t.message}"
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
