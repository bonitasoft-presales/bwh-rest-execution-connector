/**
 * Generates the <jarDependencies> XML fragment for the .impl file.
 * This script runs during generate-resources phase and writes
 * the dependency list to a Maven property used by resource filtering.
 */
import org.apache.maven.project.MavenProject

MavenProject mavenProject = project

def sb = new StringBuilder()
sb.append('<jarDependencies>\n')

// Add the connector JAR itself
sb.append("    <jarDependency>${mavenProject.artifactId}-${mavenProject.version}.jar</jarDependency>\n")

// Add compile and runtime dependencies (excluding provided/test scope)
mavenProject.artifacts.each { artifact ->
    if (artifact.scope in ['compile', 'runtime']) {
        sb.append("    <jarDependency>${artifact.artifactId}-${artifact.version}.jar</jarDependency>\n")
    }
}

sb.append('</jarDependencies>')

mavenProject.properties['impl-dependencies'] = sb.toString()
log.info("Generated impl-dependencies with ${mavenProject.artifacts.size()} artifacts")
