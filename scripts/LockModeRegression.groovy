import groovy.json.JsonOutput
String source = new File(args[0]).getText('UTF-8')
String extract(String source, String signature) {
    int start = source.indexOf(signature)
    assert start >= 0 : signature
    int end = source.indexOf('\n}\n', start)
    assert end > start
    source.substring(start, end + 3)
}
def methods = [
    'def locationModeHandler(evt)',
    'def circadianReferenceHandler(evt)',
    'def reapplyCircadianReferenceFromParent(',
    'private void reconcileCircadianPauseWithRoomDevice('
].collect { extract(source, it) }.join('\n')
def scaffolding = '''
def lockedFlag() { state.locked == true }
def debug(Object ignored) {}
def presenceClearingMode(Object mode) { mode in ['Away','Vacation'] }
def clearPresenceFromLocationMode(String mode) { state.cleared = mode }
def recomputeAndPublish(Object... ignored) { state.recomputed = true }
def circadianReferenceTrackingActive() { state.circadianReferencePaused != true }
def circadianReferenceTrackingActiveForRecompute() { state.circadianReferencePaused != true }
def followCircadianReferenceEnabled() { true }
def metaLightIntentActive() { true }
def roomDevice() { [currentValue: { String name -> 'off' }] }
def roomDeviceLabel() { 'Test Room' }
def roomReferenceTraceSnapshot() { [:] }
def referenceSnapshotForRecompute(Object value) { value }
def parentReferenceSnapshot(Object level, Object ct) { [level: level, ct: ct] }
def formatReferenceTrace(Object value) { value.toString() }
'''
def make = { boolean locked ->
    new GroovyShell(new Binding([
        state: [locked:locked, circadianReferencePaused:true, keepCircadianReferencePausedForIntentChange:true],
        useModeBasedLightingLevels:true, changeLightingLevelOnModeChange:true,
        log:[info:{ Object ignored -> }]
    ])).parse(methods + scaffolding)
}
int failures=0
int checks=0
def verify = { String name, Closure work ->
    checks++
    try { work(); println 'PASS: '+name }
    catch(Throwable e) { failures++; println 'FAIL: '+name+' '+e.message }
}
['Day','Evening','Night','Away','Vacation'].each { mode ->
    verify('locked mode '+mode+' preserves state') {
        def app=make(true)
        def before=new LinkedHashMap(app.state)
        app.locationModeHandler([value:mode])
        assert app.state == before
    }
}
verify('locked direct reference cannot clear pause or recompute') {
    def app=make(true)
    def before=new LinkedHashMap(app.state)
    app.circadianReferenceHandler([name:'colorTemperature',value:2200])
    assert app.state == before
}
verify('locked parent reference reports blocked and preserves state') {
    def app=make(true)
    def before=new LinkedHashMap(app.state)
    assert app.reapplyCircadianReferenceFromParent('mode change',75,2200).applied == false
    assert app.state == before
}
verify('lock pause is not treated as stale when custom lighting is off') {
    def app=make(true)
    app.reconcileCircadianPauseWithRoomDevice('test')
    assert app.state.circadianReferencePaused == true
}
verify('unlocked mode level change still recomputes') {
    def app=make(false)
    app.locationModeHandler([value:'Evening'])
    assert app.state.recomputed == true
}
verify('unlocked Away still clears presence') {
    def app=make(false)
    app.locationModeHandler([value:'Away'])
    assert app.state.cleared == 'Away'
}
verify('unlocked direct reference resumes stale pause and recomputes') {
    def app=make(false)
    app.circadianReferenceHandler([name:'colorTemperature',value:2200])
    assert app.state.circadianReferencePaused == false
    assert app.state.recomputed == true
}
verify('unlocked parent reference still applies') {
    def app=make(false)
    assert app.reapplyCircadianReferenceFromParent('mode change',75,2200).applied == true
    assert app.state.recomputed == true
}
println "${checks} checks, ${failures} failures"
if(failures) System.exit(1)