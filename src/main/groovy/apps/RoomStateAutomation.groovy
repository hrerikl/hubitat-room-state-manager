definition(
    name: 'Room State Automation',
    namespace: 'local',
    author: 'Local',
    description: 'Tracks room state from motion, contact, and switch events.',
    category: 'Convenience',
    iconUrl: '',
    iconX2Url: ''
)

preferences {
    page(name: 'mainPage', title: 'Room State Automation', install: true, uninstall: true) {
        section('Devices') {
            input 'motionSensors', 'capability.motionSensor', title: 'Motion sensors', multiple: true, required: false
            input 'contactSensors', 'capability.contactSensor', title: 'Contact sensors', multiple: true, required: false
            input 'switches', 'capability.switch', title: 'Switches', multiple: true, required: false
        }
        section('Options') {
            input 'vacantDelaySeconds', 'number', title: 'Seconds before vacant', defaultValue: 300, required: true
        }
    }
}

def installed() {
    initialize()
}

def updated() {
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    state.roomState = state.roomState ?: 'unknown'

    subscribe(motionSensors, 'motion', 'deviceEventHandler')
    subscribe(contactSensors, 'contact', 'deviceEventHandler')
    subscribe(switches, 'switch', 'deviceEventHandler')
    evaluateRoomState()
}

def deviceEventHandler(evt) {
    log.debug "Room input changed: ${evt.device} ${evt.name}=${evt.value}"
    evaluateRoomState()
}

def evaluateRoomState() {
    if (anyMotionActive() || anySwitchOn()) {
        setRoomState('occupied')
        return
    }

    if (anyContactOpen()) {
        setRoomState('transition')
        runIn(vacantDelaySeconds as Integer, 'markVacant')
        return
    }

    runIn(vacantDelaySeconds as Integer, 'markVacant')
}

def markVacant() {
    if (!anyMotionActive() && !anySwitchOn()) {
        setRoomState('vacant')
    }
}

private Boolean anyMotionActive() {
    motionSensors?.any { it.currentValue('motion') == 'active' } ?: false
}

private Boolean anyContactOpen() {
    contactSensors?.any { it.currentValue('contact') == 'open' } ?: false
}

private Boolean anySwitchOn() {
    switches?.any { it.currentValue('switch') == 'on' } ?: false
}

private void setRoomState(String nextState) {
    if (state.roomState == nextState) {
        return
    }

    log.info "Room state changed: ${state.roomState} -> ${nextState}"
    state.roomState = nextState
}
