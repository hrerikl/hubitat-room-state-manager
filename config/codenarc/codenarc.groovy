ruleset {
    description 'Pragmatic rules for Hubitat Groovy apps'

    ruleset('rulesets/basic.xml')
    ruleset('rulesets/braces.xml')
    ruleset('rulesets/convention.xml') {
        'CompileStatic' enabled: false
        'ImplicitClosureParameter' enabled: false
        'ImplicitReturnStatement' enabled: false
        'MethodParameterTypeRequired' enabled: false
        'MethodReturnTypeRequired' enabled: false
        'NoDef' enabled: false
        'TrailingComma' enabled: false
        'VariableTypeRequired' enabled: false
    }
    ruleset('rulesets/exceptions.xml')
    ruleset('rulesets/imports.xml')
    ruleset('rulesets/unnecessary.xml') {
        'UnnecessaryObjectReferences' enabled: false
        'UnnecessarySetter' enabled: false
    }
    ruleset('rulesets/unused.xml') {
        'UnusedMethodParameter' enabled: false
    }
}
