package hubitat

import groovy.transform.CompileDynamic
import spock.lang.Specification

@CompileDynamic
abstract class HubitatAppSpec extends Specification {
    List<Map<String, Object>> subscriptions = []
    List<Map<String, Object>> scheduledCalls = []

    Script loadApp(String path) {
        def shell = new GroovyShell(new Binding(platformBindings()))
        def script = shell.parse(new File(path))
        script.metaClass.definition = { Map ignored -> }
        script.metaClass.preferences = { Closure body -> body.call() }
        script.metaClass.page = { Map ignored, Closure body -> body.call() }
        script.metaClass.section = { String ignored, Closure body -> body.call() }
        script.metaClass.input = { Object[] ignored -> }
        script.metaClass.subscribe = { devices, String attribute, Object handler ->
            subscriptions << [devices: devices, attribute: attribute, handler: handler]
        }
        script.metaClass.unsubscribe = { -> subscriptions.clear() }
        script.metaClass.unschedule = { -> scheduledCalls.clear() }
        script.metaClass.runIn = { Integer seconds, Closure handler ->
            scheduledCalls << [seconds: seconds, handler: handler.method]
        }
        script.metaClass.runIn = { Integer seconds, Object handler ->
            scheduledCalls << [seconds: seconds, handler: handler.toString()]
        }
        script.metaClass.log = [
            debug: { Object ignored -> },
            info : { Object ignored -> },
            warn : { Object ignored -> },
            error: { Object ignored -> }
        ]
        script.run()
        script
    }

    Map<String, Object> platformBindings() {
        [state: [:]]
    }

    TestDevice device(String displayName, Map<String, String> attributes) {
        new TestDevice(displayName, attributes)
    }

    static class TestDevice {
        String displayName
        Map<String, String> attributes

        TestDevice(String displayName, Map<String, String> attributes) {
            this.displayName = displayName
            this.attributes = attributes
        }

        String currentValue(String attributeName) {
            attributes[attributeName]
        }

        String toString() {
            displayName
        }
    }
}
