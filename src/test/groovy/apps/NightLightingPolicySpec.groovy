package apps

import spock.lang.Specification

class NightLightingPolicySpec extends Specification {
    private Script handlers(String file, List<String> signatures, Map values, String stubs) {
        String source = new File("apps/${file}.groovy").getText('UTF-8').replace('\r\n', '\n')
        String methods = signatures.collect { signature ->
            int start = source.indexOf(signature)
            assert start >= 0: signature
            int end = source.indexOf('\n}\n', start)
            assert end > start: signature
            source.substring(start, end + 3)
        }.join('\n')
        return new GroovyShell(new Binding(values)).parse(methods + '\n' + stubs)
    }

    private Script circadian(String mode) {
        handlers('SimpleCircadianLighting', [
            'private Map limitedReference(', 'private Integer limitedStep(',
            'private Integer maxLevelChange()', 'private Integer maxCtChange()',
            'private Integer settingInteger(', 'private Integer clampInteger(',
            'private BigDecimal clampDecimal(', 'private Boolean nightReferenceActive()',
            'private Integer referenceColorTemperature(', 'private Integer referenceLevel(',
            'private Integer modeMinimumLevel()'
        ], [mode:mode, nightModeName:'Night', eveningModeName:'Evening',
            nightReferenceLevel:5, eveningMinimumLevel:85, dayMinimumLevel:60,
            eveningReferenceCT:2700, nightReferenceCT:2200, outdoorLuxForMaxLevel:40000,
            maxLevelChangePerPublish:null, maxCtChangePerPublish:null,
            referenceBulb:[level:85, ct:3500]], '''
            def currentLocationModeName() { mode }
            def minCt() { 2200 }
            def maxCt() { 6500 }
            def currentInteger(device, name, fallback) { name == 'level' ? device.level : device.ct }
        ''')
    }

    def 'Night immediately reaches the dim warm target without the daytime ramp'() {
        given:
        def app = circadian('Night')
        expect:
        app.referenceLevel(0) == 5
        app.referenceColorTemperature(6500) == 2200
        app.limitedReference(5, 2200) == [level:5, ct:2200]
    }

    def 'Evening stays bright and warm even with zero outdoor lux'() {
        given:
        def app = circadian('Evening')
        app.referenceBulb.level = 20
        expect:
        app.referenceLevel(0) == 85
        app.referenceColorTemperature(2200) == 2700
        app.referenceLevel(10000) >= 85
        app.limitedReference(85, 2700).level == 85
    }

    def 'Day still uses gradual changes and the outdoor color reference'() {
        given:
        def app = circadian('Day')
        expect:
        app.limitedReference(60, 2200) == [level:70, ct:3100]
        app.referenceColorTemperature(5000) == 5000
    }

    private Script house(String mode) {
        handlers('SimpleHouseIntentLighting', [
            'def locationModeHandler(evt)', 'private Boolean preserveCustomInMode(',
            'def commitPendingIntent()', 'private void clearPendingIntent()',
            'private void enforceAutomaticReturn(', 'private Boolean houseNightActive()',
            'def houseCustomLightingHandler(evt)'
        ], [app:[label:"Test House"], state:[pendingCustom:true, pendingSceneName:'Party', commitReason:'scene Party',
                pendingLevel:55, pendingCt:2700, manualOverrideStartedAt:1000L],
            mode:mode, nightModeName:'Night', settings:[preserveCustomModeNames:['Evening','Night']],
            clock:2000L, manualOverrideHours:2, timers:[], automaticReturns:0,
            log:[info:{ Object ignored -> }]], '''
            def now() { clock }
            def currentLocationModeName() { mode }
            def houseIntentIsCustom() { state.pendingCustom == true }
            def asList(value) { value ?: [] }
            def safeInteger(value, fallback) { value == null ? fallback : value as Integer }
            def debug(Object ignored) {}
            def unschedule(Object handler) { timers << handler.toString() }
            def returnToHouseReference(Boolean announce = true) { clearPendingIntent(); automaticReturns++ }
            def roomDevice() { throw new AssertionError('Stale or Night commit must not actuate the room') }
        ''')
    }

    def 'Night always clears Party and pending timers even when Night was in preserve modes'() {
        given:
        def app = house('Night')
        when:
        app.locationModeHandler([value:'Night'])
        then:
        app.automaticReturns == 1
        app.state.pendingCustom == false
        app.state.pendingLevel == null
        app.state.pendingCt == null
        app.state.commitReason == null
        app.timers.size() == 2
    }

    def 'queued Party commit cannot reapply after entering Night'() {
        given:
        def app = house('Night')
        when:
        app.commitPendingIntent()
        then:
        app.automaticReturns == 1
        app.state.pendingCustom == false
    }

    def 'Evening preserves an active Party hold before expiry'() {
        given:
        def app = house('Evening')
        when:
        app.locationModeHandler([value:'Evening'])
        app.enforceAutomaticReturn('watchdog')
        then:
        app.automaticReturns == 0
        app.state.pendingSceneName == 'Party'
    }

    def 'expired manual override clears even if no mode event arrived'() {
        given:
        def app = house('Evening')
        app.clock = 1000L + 7200000L
        when:
        app.enforceAutomaticReturn('watchdog')
        then:
        app.automaticReturns == 1
        app.state.manualOverrideStartedAt == null
    }

    def 'Night watchdog clears a hold after a missed mode event'() {
        given:
        def app = house('Night')
        when:
        app.enforceAutomaticReturn('watchdog')
        then:
        app.automaticReturns == 1
    }

    def 'stale scene callback after reset cannot touch the room'() {
        given:
        def app = house('Evening')
        app.clearPendingIntent()
        when:
        app.commitPendingIntent()
        then:
        app.automaticReturns == 0
    }

    private Script room(String mode, String custom, String name = 'Media Room') {
        handlers('SimpleRoomState', ['private void setLocked(', 'private void resetNightManualLighting(', 'def locationModeHandler(evt)', 'private Boolean defaultNightMotionProtection()',
            'private Boolean nightMotionProtectionEnabled()',
            'private Boolean automaticNightLightingCapped()', 'private Integer capAutomaticNightLevel('],
            [state:[:], autoClearLock:"autoClearLock", unlockImpliesActivity:false, useModeBasedLightingLevels:false, changeLightingLevelOnModeChange:false, mode:mode, custom:custom, roomName:name, settings:[:], nightMotionMaximumLevel:3], '''
            def safeRoomName() { roomName }
            def lockedFlag() { false }
            def setLockedFlag(value) { state.locked = value }
            def unschedule(value) {}
            def componentSwitchOff(value) {}
            def asleepFlag() { false }
            def hasRecentActivityWithinOccupiedTimeout() { false }
            def setOccupiedFlag(value) {}
            def setEngagedFlag(value) {}
            def refreshCourtesyState() {}
            def presenceClearingMode(value) { false }
            def clearCustomLightingSilently(reason) { custom = 'off' }
            def resumeCircadianReference(reason) {}
            def recomputeAndPublish() {}
            def debug(Object ignored) {}
            def houseIntentProfile() { false }
            def settingBool(name, fallback) { settings.containsKey(name) ? settings[name] : fallback }
            def currentLocationModeName() { mode }
            def roomDevice() { [currentValue:{ name -> custom }] }
            def normalizedPercent(value, fallback) { value == null ? fallback : value as Integer }
        ''')
    }

    def 'Media Room caps automatic Night motion and courtesy but preserves Off'() {
        given:
        def app = room('Night','off')
        expect:
        app.capAutomaticNightLevel(100) == 3
        app.capAutomaticNightLevel(30) == 3
        app.capAutomaticNightLevel(0) == 0
    }

    def 'Night clears an earlier local scene before applying automatic protection'() {
        given:
        def app = room('Night','on')
        when:
        app.locationModeHandler([value:'Night'])
        then:
        app.custom == 'off'
        app.capAutomaticNightLevel(100) == 3
    }
    def 'TV unlock after Night started clears the scene held by the lock'() {
        given:
        def app = room('Night','on')
        when:
        app.setLocked(false, 'TV off')
        then:
        app.custom == 'off'
        app.capAutomaticNightLevel(100) == 3
    }
    def 'Evening is never subject to the Media Room Night cap'() {
        expect:
        room('Evening','off').capAutomaticNightLevel(85) == 85
    }

    def 'deliberate local adjustment can brighten a room during Night'() {
        expect:
        room('Night','on').capAutomaticNightLevel(55) == 55
    }

    def 'other rooms are not silently given the Media Room cap'() {
        expect:
        room('Night','off','Kitchen').capAutomaticNightLevel(5) == 5
    }

    private Script modes() {
        handlers('SimpleModeManager', ['private void evaluateNight(', 'private void resetNightReadiness()',
            'def activateNightIfStillReady()', 'private void recordNightDecision(',
            'private Boolean someoneHome()', 'def modeWatchdog()', 'private void returnHomeFromAway('],
            [app:[label:"Test Mode Manager"], state:[:], clock:1000000L, mode:'Evening', nightModeName:'Night', eveningModeName:'Evening',
                dayModeName:'Day', awayModeName:'Away', vacationModeName:'Vacation',
                presenceSensors:[[currentPresence:'present']], presenceSwitches:[],
                ready:true, window:true, extended:false, scheduled:[],
                log:[info:{ Object ignored -> }]], '''
            def now() { clock }
            def currentLocationModeName() { mode }
            def presenceSummary() { 'test presence inputs' }
            def evaluatePresenceMode(String reason) { if (!someoneHome()) mode = 'Away' }
            def nightWindowActive() { window }
            def nightExtended() { extended }
            def nightReadyRoomsReady() { ready }
            def nightBlockingRoomLabels() { 'Kitchen=Occupied' }
            def nightReadySeconds() { 600 }
            def scheduleNightExtensionRecheck(value) {}
            def eveningWindowActive() { true }
            def currentHomeModeName() { 'Evening' }
            def formatTime(value) { value.toString() }
            def setModeIfNeeded(value, reason) { mode = value }
            def runIn(seconds, handler, options) { scheduled << [seconds:seconds, handler:handler] }
            def unschedule(handler) { scheduled.clear() }
            def debug(Object ignored) {}
        ''')
    }

    def 'ready room events and watchdog do not restart the Night quiet timer'() {
        given:
        def app = modes()
        when:
        app.evaluateNight('first ready')
        app.clock += 300000L
        app.modeWatchdog()
        app.evaluateNight('another ready room event')
        then:
        app.state.nightReadySince == 1000000L
        app.state.nightReadyDeadline == 1600000L
        app.scheduled.size() == 1
        when:
        app.clock = 1600000L
        app.activateNightIfStillReady()
        then:
        app.mode == 'Night'
        app.state.nightReadySince == null
    }

    def 'occupied room cancels the pending Night transition'() {
        given:
        def app = modes()
        app.evaluateNight('ready')
        when:
        app.ready = false
        app.evaluateNight('motion')
        then:
        app.state.nightReadySince == null
        app.scheduled.empty
        app.state.lastNightDecision.contains('Kitchen=Occupied')
    }

    def 'absent presence is diagnosed rather than silently asserting somebody home'() {
        given:
        def app = modes()
        app.presenceSensors = [[currentPresence:'not present']]
        when:
        app.evaluateNight('check')
        then:
        app.mode == 'Away'
        app.state.lastNightDecision.contains('no configured presence')
    }

    def 'explicit home override can supplement missing phone presence'() {
        given:
        def app = modes()
        app.presenceSensors = [[currentPresence:'not present']]
        app.presenceSwitches = [[currentSwitch:'on']]
        expect:
        app.someoneHome()
    }

    def 'late arrival watchdog evaluates bedtime instead of remaining Evening indefinitely'() {
        given:
        def app = modes()
        app.mode = 'Away'
        when:
        app.modeWatchdog()
        then:
        app.mode == 'Evening'
        app.state.nightReadyDeadline == 1600000L
    }
}