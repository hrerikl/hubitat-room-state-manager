package apps

import spock.lang.Specification

class HouseIntentCacheSpec extends Specification {
    private Script parent(List children) {
        String source = new File('apps/SimpleHome.groovy').getText('UTF-8').replace('\r\n', '\n')
        String methods = [
            'private def houseIntentChildApp()', 'private Long houseIntentChildCacheMs()',
            'private List houseIntentChildApps()', 'private Boolean houseIntentRoomChild(',
            'private def effectiveCircadianReferenceBulb()', 'private def houseIntentReferenceBulb()'
        ].collect { signature ->
            int start = source.indexOf(signature)
            int end = source.indexOf('\n}\n', start)
            assert start >= 0 && end > start
            source.substring(start, end + 3)
        }.join('\n')
        new GroovyShell(new Binding([
            app: [id:101], children: children, clock:1000000L, scans:0,
            houseIntentChildIdCache:new java.util.concurrent.ConcurrentHashMap(),
            useHouseIntentVirtualRoom:true, defaultCircadianReferenceBulb:'raw'
        ])).parse(methods + '''
            def now() { clock }
            def safeLong(value, fallback) { value == null ? fallback : value as Long }
            def roomStateChildById(id) { children.find { it.id == id } }
            def roomStateChildApps() { scans++; children }
        ''')
    }

    def 'cached lookup avoids enumerating every child again'() {
        given:
        def room = new Room()
        def app = parent([room])
        expect:
        app.effectiveCircadianReferenceBulb() == 'reference-House Intent'
        app.effectiveCircadianReferenceBulb() == 'reference-House Intent'
        app.scans == 1
    }

    def 'changed room stops driving the house reference before cache expiry'() {
        given:
        def room = new Room()
        def app = parent([room])
        assert app.effectiveCircadianReferenceBulb() == 'reference-House Intent'
        when:
        room.profile = 'standard'
        room.roomName = 'Kitchen'
        then:
        app.effectiveCircadianReferenceBulb() == 'raw'
    }

    def 'deleted cached room is replaced immediately'() {
        given:
        def oldRoom = new Room()
        def children = [oldRoom]
        def app = parent(children)
        assert app.houseIntentChildApp().is(oldRoom)
        when:
        def replacement = new Room(id:'301')
        children.clear()
        children.add(replacement)
        then:
        app.houseIntentChildApp().is(replacement)
    }

    static class Room {
        String id = '300'
        String profile = 'houseIntent'
        String roomName = 'House Intent'
        String getLabel() { 'Room ' + roomName }
        String getRoomProfile() { profile }
        String getConfiguredRoomName() { roomName }
        String getManagedMetaLightDevice() { 'reference-' + roomName }
    }
}
