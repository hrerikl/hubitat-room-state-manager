package apps

import hubitat.HubitatAppSpec

class RoomStateAutomationSpec extends HubitatAppSpec {

    def 'motion marks the room occupied'() {
        given:
        def app = loadApp('src/main/groovy/apps/RoomStateAutomation.groovy')
        app.motionSensors = [device('motion-1', [motion: 'active'])]
        app.contactSensors = []
        app.switches = []
        app.vacantDelaySeconds = 300

        when:
        app.initialize()

        then:
        app.state.roomState == 'occupied'
    }

    def 'open contact marks the room in transition and schedules vacancy check'() {
        given:
        def app = loadApp('src/main/groovy/apps/RoomStateAutomation.groovy')
        app.motionSensors = [device('motion-1', [motion: 'inactive'])]
        app.contactSensors = [device('door-1', [contact: 'open'])]
        app.switches = []
        app.vacantDelaySeconds = 120

        when:
        app.initialize()

        then:
        app.state.roomState == 'transition'
        scheduledCalls == [[seconds: 120, handler: 'markVacant']]
    }

    def 'inactive room becomes vacant when vacancy check runs'() {
        given:
        def app = loadApp('src/main/groovy/apps/RoomStateAutomation.groovy')
        app.motionSensors = [device('motion-1', [motion: 'inactive'])]
        app.contactSensors = []
        app.switches = [device('lamp-1', [switch: 'off'])]
        app.vacantDelaySeconds = 60
        app.initialize()

        when:
        app.markVacant()

        then:
        app.state.roomState == 'vacant'
    }
}
