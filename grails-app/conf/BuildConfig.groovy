//Use a custom plugins dir, because different branches use different plugin versions
grails.project.plugins.dir = "../local-plugins/risk-analytics-pc-cashflow-master"

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
            runtime "org.pillarone:risk-analytics-core:2.0-SNAPSHOT"
            runtime("org.pillarone:risk-analytics-commons:2.0-SNAPSHOT") { transitive = false }
            runtime("org.pillarone:risk-analytics-property-casualty:2.0-SNAPSHOT") { transitive = false }
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
//grails.plugin.location.'risk-analytics-core' = "../risk-analytics-core-master"
//grails.plugin.location.'risk-analytics-commons' = "../risk-analytics-commons-master"
//grails.plugin.location.'risk-analytics-property-casualty' = "../risk-analytics-property-casualty-master"

grails.project.repos.default = "pillarone"

grails.project.dependency.distribution = {
    String password = ""
    String user = ""
    try {
        Properties properties = new Properties()
        String version = new GroovyClassLoader().loadClass('RiskAnalyticsPcCashflowGrailsPlugin').newInstance().version
        properties.load(new File("${userHome}/deployInfo.properties").newInputStream())
        user = properties.get("user")
        password = properties.get("password")

        String scpUrl = properties.get( ( version?.endsWith('-SNAPSHOT')) ? "urlSnapshot" : "url" )
	    remoteRepository(id: "pillarone", url: scpUrl) {
        	authentication username: user, password: password
    	}

    } catch (Throwable t) {
        System.err.println("Error: " + t.message)
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
