package apps

import spock.lang.Specification
import spock.lang.Unroll

class RoomLightingInitializationSpec extends Specification {
    private Script lighting(Map snapshot) {
        String source = new File('apps/SimpleRoomLighting.groovy').getText('UTF-8').replace('\r\n', '\n')
        int start = source.indexOf('def initialize() {')
        int end = source.indexOf('\n}\n', start)
        assert start >= 0 && end > start
        new GroovyShell(new Binding([
            atomicState: [:], snap: snapshot, outputs: [], subscriptions: [],
            parent: [recoveryDevice: { null }]
        ])).parse(source.substring(start, end + 3) + '''
            def propertyMissing(String name) { name }
            def cacheDebugEnabled(value) {}
            def updateAppLabel() {}
            def roomLightingAllowed() { true }
            def roomDevice() { 'room' }
            def overrideSwitches() { [] }
            def subscribe(Object... args) { subscriptions << args }
            def syncRoomAnnouncementMessages(room) {}
            def roomSnapshot(Boolean fresh) { assert fresh; snap }
            def debug(message) {}
            def reassessLighting(String reason, Map snapshot) { outputs << [reason, snapshot] }
        ''')
    }

    @Unroll
    def 'initialization preserves physical outputs when #indicator reports a lock'() {
        given:
        def snapshot = [valid:true, roomState:'Occupied', lockedEnabled:'off', lock:'unlocked',
                        sw:'on', intent:'On', level:100, ct:3500] + [(indicator): value]
        def app = lighting(snapshot)

        when:
        app.initialize()

        then:
        app.outputs.empty
        !app.subscriptions.empty

        where:
        indicator       | value
        'roomState'     | 'Locked'
        'lockedEnabled' | 'on'
        'lock'          | 'locked'
    }

    def 'initialization still applies lighting for an unlocked room'() {
        given:
        def snapshot = [valid:true, roomState:'Occupied', lockedEnabled:'off', lock:'unlocked',
                        sw:'on', intent:'On', level:85, ct:2700]
        def app = lighting(snapshot)

        when:
        app.initialize()

        then:
        app.outputs == [['initialize', snapshot]]
    }
}
