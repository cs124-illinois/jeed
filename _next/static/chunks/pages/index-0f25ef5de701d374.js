(self.webpackChunk_N_E=self.webpackChunk_N_E||[]).push([[332],{2646:(e,t,r)=>{"use strict";Object.defineProperty(t,"__esModule",{value:!0}),t.AppliedMutation=t.MutationLocation=t.FlatFeaturesResults=t.FlatFeaturesResult=t.FlatUnitFeatures=t.FlatMethodFeatures=t.FlatClassFeatures=t.FeatureValue=t.LocatedFeature=t.ORDERED_FEATURES=t.KOTLIN_FEATURES=t.JAVA_FEATURES=t.ALL_FEATURES=t.STRUCTURAL_FEATURES=t.KOTLIN_ONLY_FEATURES=t.JAVA_ONLY_FEATURES=t.Feature=t.FlatComplexityResults=t.FlatComplexityResult=t.FlatMethodComplexity=t.FlatClassComplexity=t.KtlintResults=t.KtlintError=t.CheckstyleResults=t.CheckstyleError=t.CompiledSourceResult=t.CompilationMessage=t.TemplatedSourceResult=t.Snippet=t.Request=t.TaskArguments=t.MutationsArguments=t.ContainerExecutionArguments=t.SourceExecutionArguments=t.ClassLoaderConfiguration=t.KtLintArguments=t.CheckstyleArguments=t.KompilationArguments=t.CompilationArguments=t.SnippetArguments=t.ServerStatus=t.intervalDuration=t.Interval=t.SourceLocation=t.SourceRange=t.Location=t.FileType=t.Permission=t.FlatSource=t.Task=void 0,t.Response=t.FailedTasks=t.ExecutionFailedResult=t.DisassembleFailed=t.MutationsFailed=t.FeaturesFailed=t.ComplexityFailed=t.SourceError=t.KtlintFailed=t.CheckstyleFailed=t.CompilationFailed=t.CompilationError=t.SnippetTransformationFailed=t.SnippetTransformationError=t.TemplatingFailed=t.TemplatingError=t.CompletedTasks=t.ContainerExecutionResults=t.SourceTaskResults=t.KILL_REASONS=t.PermissionRequest=t.OutputLine=t.Console=t.ThrownException=t.DisassembleResults=t.MutationsResults=t.MutatedSource=void 0;let i=r(4055);t.Task=(0,i.Union)((0,i.Literal)("template"),(0,i.Literal)("snippet"),(0,i.Literal)("compile"),(0,i.Literal)("kompile"),(0,i.Literal)("checkstyle"),(0,i.Literal)("ktlint"),(0,i.Literal)("complexity"),(0,i.Literal)("execute"),(0,i.Literal)("cexecute"),(0,i.Literal)("features"),(0,i.Literal)("mutations"),(0,i.Literal)("disassemble")),t.FlatSource=(0,i.Record)({path:i.String,contents:i.String}),t.Permission=(0,i.Record)({klass:i.String,name:i.String}).And((0,i.Partial)({actions:i.String})),t.FileType=(0,i.Union)((0,i.Literal)("JAVA"),(0,i.Literal)("KOTLIN")),t.Location=(0,i.Record)({line:i.Number,column:i.Number}),t.SourceRange=(0,i.Record)({start:t.Location,end:t.Location}).And((0,i.Partial)({source:i.String})),t.SourceLocation=(0,i.Record)({source:i.String,line:i.Number,column:i.Number}),t.Interval=(0,i.Record)({start:i.String.withConstraint(e=>!isNaN(Date.parse(e))),end:i.String.withConstraint(e=>!isNaN(Date.parse(e)))}),t.intervalDuration=e=>new Date(e.end).valueOf()-new Date(e.start).valueOf(),t.ServerStatus=(0,i.Record)({tasks:(0,i.Array)(t.Task),started:i.String.withConstraint(e=>!isNaN(Date.parse(e))),hostname:i.String,versions:(0,i.Record)({jeed:i.String,compiler:i.String,kompiler:i.String}),counts:(0,i.Record)({submitted:i.Number,completed:i.Number,saved:i.Number}),cache:(0,i.Record)({inUse:i.Boolean,sizeInMB:i.Number,hits:i.Number,misses:i.Number,hitRate:i.Number,evictionCount:i.Number,averageLoadPenalty:i.Number})}).And((0,i.Partial)({lastRequest:i.String.withConstraint(e=>!isNaN(Date.parse(e)))})),t.SnippetArguments=(0,i.Partial)({indent:i.Number,fileType:t.FileType,noEmptyMain:i.Boolean}),t.CompilationArguments=(0,i.Partial)({wError:i.Boolean,XLint:i.String,enablePreview:i.Boolean,useCache:i.Boolean,waitForCache:i.Boolean,parameters:i.Boolean,debugInfo:i.Boolean}),t.KompilationArguments=(0,i.Partial)({verbose:i.Boolean,allWarningsAsErrors:i.Boolean,useCache:i.Boolean,waitForCache:i.Boolean}),t.CheckstyleArguments=(0,i.Partial)({sources:(0,i.Array)(i.String),failOnError:i.Boolean}),t.KtLintArguments=(0,i.Partial)({sources:(0,i.Array)(i.String),failOnError:i.Boolean,indent:i.Number,maxLineLength:i.Number,script:i.Boolean}),t.ClassLoaderConfiguration=(0,i.Partial)({whitelistedClasses:(0,i.Array)(i.String),blacklistedClasses:(0,i.Array)(i.String),unsafeExceptions:(0,i.Array)(i.String),isolatedClasses:(0,i.Array)(i.String)}),t.SourceExecutionArguments=(0,i.Partial)({klass:i.String,method:i.String,timeout:i.Number,permissions:(0,i.Array)(t.Permission),maxExtraThreads:i.Number,maxOutputLines:i.Number,maxIOBytes:i.Number,classLoaderConfiguration:t.ClassLoaderConfiguration,dryRun:i.Boolean,waitForShutdown:i.Boolean,returnTimeout:i.Number,permissionBlackList:i.Boolean,cpuTimeout:i.Number,pollInterval:i.Number}),t.ContainerExecutionArguments=(0,i.Partial)({klass:i.String,method:i.String,image:i.String,timeout:i.Number,maxOutputLines:i.Number,containerArguments:i.String}),t.MutationsArguments=(0,i.Partial)({limit:i.Number,suppressWithComments:i.Boolean}),t.TaskArguments=(0,i.Partial)({snippet:t.SnippetArguments,compilation:t.CompilationArguments,kompilation:t.KompilationArguments,checkstyle:t.CheckstyleArguments,ktlint:t.KtLintArguments,execution:t.SourceExecutionArguments,cexecution:t.ContainerExecutionArguments,mutations:t.MutationsArguments}),t.Request=(0,i.Record)({tasks:(0,i.Array)(t.Task),label:i.String}).And((0,i.Partial)({sources:(0,i.Array)(t.FlatSource),templates:(0,i.Array)(t.FlatSource),snippet:i.String,arguments:t.TaskArguments,checkForSnippet:i.Boolean})),t.Snippet=(0,i.Record)({sources:(0,i.Dictionary)(i.String),originalSource:i.String,rewrittenSource:i.String,snippetRange:t.SourceRange,wrappedClassName:i.String,looseCodeMethodName:i.String,fileType:t.FileType}),t.TemplatedSourceResult=(0,i.Record)({sources:(0,i.Dictionary)(i.String),originalSources:(0,i.Dictionary)(i.String)}),t.CompilationMessage=(0,i.Record)({kind:i.String,message:i.String}).And((0,i.Partial)({location:t.SourceLocation})),t.CompiledSourceResult=(0,i.Record)({messages:(0,i.Array)(t.CompilationMessage),compiled:i.String.withConstraint(e=>!isNaN(Date.parse(e))),interval:t.Interval,compilerName:i.String,cached:i.Boolean}),t.CheckstyleError=(0,i.Record)({severity:i.String,location:t.SourceLocation,message:i.String}).And((0,i.Partial)({sourceName:i.String})),t.CheckstyleResults=(0,i.Record)({errors:(0,i.Array)(t.CheckstyleError)}),t.KtlintError=(0,i.Record)({ruleId:i.String,detail:i.String,location:t.SourceLocation}),t.KtlintResults=(0,i.Record)({errors:(0,i.Array)(t.KtlintError)}),t.FlatClassComplexity=(0,i.Record)({name:i.String,path:i.String,range:t.SourceRange,complexity:i.Number}),t.FlatMethodComplexity=t.FlatClassComplexity,t.FlatComplexityResult=(0,i.Record)({source:i.String,classes:(0,i.Array)(t.FlatClassComplexity),methods:(0,i.Array)(t.FlatMethodComplexity)}),t.FlatComplexityResults=(0,i.Record)({results:(0,i.Array)(t.FlatComplexityResult)}),t.Feature=(0,i.Union)((0,i.Literal)("EMPTY"),(0,i.Literal)("LOCAL_VARIABLE_DECLARATIONS"),(0,i.Literal)("VARIABLE_ASSIGNMENTS"),(0,i.Literal)("VARIABLE_REASSIGNMENTS"),(0,i.Literal)("FINAL_VARIABLE"),(0,i.Literal)("UNARY_OPERATORS"),(0,i.Literal)("ARITHMETIC_OPERATORS"),(0,i.Literal)("BITWISE_OPERATORS"),(0,i.Literal)("ASSIGNMENT_OPERATORS"),(0,i.Literal)("TERNARY_OPERATOR"),(0,i.Literal)("COMPARISON_OPERATORS"),(0,i.Literal)("LOGICAL_OPERATORS"),(0,i.Literal)("PRIMITIVE_CASTING"),(0,i.Literal)("IF_STATEMENTS"),(0,i.Literal)("ELSE_STATEMENTS"),(0,i.Literal)("ELSE_IF"),(0,i.Literal)("ARRAYS"),(0,i.Literal)("ARRAY_ACCESS"),(0,i.Literal)("ARRAY_LITERAL"),(0,i.Literal)("MULTIDIMENSIONAL_ARRAYS"),(0,i.Literal)("FOR_LOOPS"),(0,i.Literal)("ENHANCED_FOR"),(0,i.Literal)("WHILE_LOOPS"),(0,i.Literal)("DO_WHILE_LOOPS"),(0,i.Literal)("BREAK"),(0,i.Literal)("CONTINUE"),(0,i.Literal)("NESTED_IF"),(0,i.Literal)("NESTED_FOR"),(0,i.Literal)("NESTED_WHILE"),(0,i.Literal)("NESTED_DO_WHILE"),(0,i.Literal)("NESTED_CLASS"),(0,i.Literal)("NESTED_LOOP"),(0,i.Literal)("METHOD"),(0,i.Literal)("RETURN"),(0,i.Literal)("CONSTRUCTOR"),(0,i.Literal)("GETTER"),(0,i.Literal)("SETTER"),(0,i.Literal)("METHOD_CALL"),(0,i.Literal)("STRING"),(0,i.Literal)("NULL"),(0,i.Literal)("CASTING"),(0,i.Literal)("TYPE_INFERENCE"),(0,i.Literal)("INSTANCEOF"),(0,i.Literal)("CLASS"),(0,i.Literal)("IMPLEMENTS"),(0,i.Literal)("INTERFACE"),(0,i.Literal)("EXTENDS"),(0,i.Literal)("SUPER"),(0,i.Literal)("OVERRIDE"),(0,i.Literal)("TRY_BLOCK"),(0,i.Literal)("FINALLY"),(0,i.Literal)("ASSERT"),(0,i.Literal)("THROW"),(0,i.Literal)("THROWS"),(0,i.Literal)("NEW_KEYWORD"),(0,i.Literal)("THIS"),(0,i.Literal)("REFERENCE_EQUALITY"),(0,i.Literal)("CLASS_FIELD"),(0,i.Literal)("EQUALITY"),(0,i.Literal)("VISIBILITY_MODIFIERS"),(0,i.Literal)("STATIC_METHOD"),(0,i.Literal)("FINAL_METHOD"),(0,i.Literal)("ABSTRACT_METHOD"),(0,i.Literal)("STATIC_FIELD"),(0,i.Literal)("FINAL_FIELD"),(0,i.Literal)("FINAL_CLASS"),(0,i.Literal)("ABSTRACT_CLASS"),(0,i.Literal)("IMPORT"),(0,i.Literal)("ANONYMOUS_CLASSES"),(0,i.Literal)("LAMBDA_EXPRESSIONS"),(0,i.Literal)("GENERIC_CLASS"),(0,i.Literal)("SWITCH"),(0,i.Literal)("SWITCH_EXPRESSION"),(0,i.Literal)("STREAM"),(0,i.Literal)("ENUM"),(0,i.Literal)("COMPARABLE"),(0,i.Literal)("RECORD"),(0,i.Literal)("BOXING_CLASSES"),(0,i.Literal)("TYPE_PARAMETERS"),(0,i.Literal)("PRINT_STATEMENTS"),(0,i.Literal)("DOT_NOTATION"),(0,i.Literal)("DOTTED_METHOD_CALL"),(0,i.Literal)("DOTTED_VARIABLE_ACCESS"),(0,i.Literal)("BLOCK_START"),(0,i.Literal)("BLOCK_END"),(0,i.Literal)("STATEMENT_START"),(0,i.Literal)("STATEMENT_END"),(0,i.Literal)("NESTED_METHOD"),(0,i.Literal)("JAVA_PRINT_STATEMENTS"),(0,i.Literal)("REQUIRE_OR_CHECK"),(0,i.Literal)("FOR_LOOP_STEP"),(0,i.Literal)("ELVIS_OPERATOR"),(0,i.Literal)("FOR_LOOP_RANGE"),(0,i.Literal)("SECONDARY_CONSTRUCTOR"),(0,i.Literal)("JAVA_EQUALITY"),(0,i.Literal)("COMPANION_OBJECT"),(0,i.Literal)("HAS_COMPANION_OBJECT"),(0,i.Literal)("NULLABLE_TYPE"),(0,i.Literal)("WHEN_STATEMENT"),(0,i.Literal)("EXPLICIT_TYPE"),(0,i.Literal)("DATA_CLASS"),(0,i.Literal)("OPEN_CLASS"),(0,i.Literal)("OPEN_METHOD"),(0,i.Literal)("COLLECTION_INDEXING"),(0,i.Literal)("MULTILEVEL_COLLECTION_INDEXING"),(0,i.Literal)("SINGLETON"),(0,i.Literal)("FUNCTIONAL_INTERFACE"),(0,i.Literal)("ANONYMOUS_FUNCTION"),(0,i.Literal)("ABSTRACT_FIELD"),(0,i.Literal)("IF_EXPRESSIONS"),(0,i.Literal)("TRY_EXPRESSIONS"),(0,i.Literal)("WHEN_EXPRESSIONS"),(0,i.Literal)("SAFE_CALL_OPERATOR"),(0,i.Literal)("UNSAFE_CALL_OPERATOR"),(0,i.Literal)("WHEN_ENTRY"),(0,i.Literal)("LAST_WHEN_ENTRY")),t.JAVA_ONLY_FEATURES=new Set((0,i.Array)(t.Feature).check(["TERNARY_OPERATOR","ARRAY_ACCESS","MULTIDIMENSIONAL_ARRAYS","ENHANCED_FOR","THROWS","NEW_KEYWORD","FINAL_METHOD","FINAL_CLASS","SWITCH","SWITCH_EXPRESSION","STREAM","RECORD","BOXING_CLASSES"])),t.KOTLIN_ONLY_FEATURES=new Set((0,i.Array)(t.Feature).check(["NESTED_METHOD","JAVA_PRINT_STATEMENTS","REQUIRE_OR_CHECK","FOR_LOOP_STEP","ELVIS_OPERATOR","FOR_LOOP_RANGE","SECONDARY_CONSTRUCTOR","JAVA_EQUALITY","COMPANION_OBJECT","HAS_COMPANION_OBJECT","NULLABLE_TYPE","WHEN_STATEMENT","EXPLICIT_TYPE","DATA_CLASS","OPEN_CLASS","OPEN_METHOD","COLLECTION_INDEXING","MULTILEVEL_COLLECTION_INDEXING","SINGLETON","FUNCTIONAL_INTERFACE","ANONYMOUS_FUNCTION","ABSTRACT_FIELD","IF_EXPRESSIONS","TRY_EXPRESSIONS","WHEN_EXPRESSIONS","SAFE_CALL_OPERATOR","UNSAFE_CALL_OPERATOR","WHEN_ENTRY","LAST_WHEN_ENTRY"])),t.STRUCTURAL_FEATURES=new Set(["BLOCK_START","BLOCK_END","STATEMENT_START","STATEMENT_END"]),t.ALL_FEATURES=new Set(t.Feature.alternatives.map(e=>e.value)),t.JAVA_FEATURES=new Set([...t.ALL_FEATURES].filter(e=>"EMPTY"!==e&&!t.KOTLIN_ONLY_FEATURES.has(e)&&!t.STRUCTURAL_FEATURES.has(e))),t.KOTLIN_FEATURES=new Set([...t.ALL_FEATURES].filter(e=>"EMPTY"!==e&&!t.JAVA_ONLY_FEATURES.has(e)&&!t.STRUCTURAL_FEATURES.has(e))),t.ORDERED_FEATURES=(0,i.Array)(t.Feature).check(["LOCAL_VARIABLE_DECLARATIONS","VARIABLE_ASSIGNMENTS","VARIABLE_REASSIGNMENTS","FINAL_VARIABLE","UNARY_OPERATORS","ARITHMETIC_OPERATORS","BITWISE_OPERATORS","ASSIGNMENT_OPERATORS","TERNARY_OPERATOR","COMPARISON_OPERATORS","LOGICAL_OPERATORS","PRIMITIVE_CASTING","IF_STATEMENTS","ELSE_STATEMENTS","ELSE_IF","ARRAYS","ARRAY_ACCESS","ARRAY_LITERAL","MULTIDIMENSIONAL_ARRAYS","FOR_LOOPS","ENHANCED_FOR","WHILE_LOOPS","DO_WHILE_LOOPS","BREAK","CONTINUE","NESTED_IF","NESTED_FOR","NESTED_WHILE","NESTED_DO_WHILE","NESTED_CLASS","NESTED_LOOP","METHOD","RETURN","CONSTRUCTOR","GETTER","SETTER","STRING","NULL","CASTING","TYPE_INFERENCE","INSTANCEOF","CLASS","IMPLEMENTS","INTERFACE","EXTENDS","SUPER","OVERRIDE","TRY_BLOCK","FINALLY","ASSERT","THROW","THROWS","NEW_KEYWORD","THIS","REFERENCE_EQUALITY","CLASS_FIELD","EQUALITY","VISIBILITY_MODIFIERS","STATIC_METHOD","FINAL_METHOD","ABSTRACT_METHOD","STATIC_FIELD","FINAL_FIELD","ABSTRACT_FIELD","FINAL_CLASS","ABSTRACT_CLASS","IMPORT","ANONYMOUS_CLASSES","LAMBDA_EXPRESSIONS","GENERIC_CLASS","SWITCH","STREAM","ENUM","COMPARABLE","RECORD","BOXING_CLASSES","TYPE_PARAMETERS","PRINT_STATEMENTS","DOT_NOTATION","DOTTED_METHOD_CALL","DOTTED_VARIABLE_ACCESS","BLOCK_START","BLOCK_END","STATEMENT_START","STATEMENT_END","NESTED_METHOD","JAVA_PRINT_STATEMENTS","REQUIRE_OR_CHECK","FOR_LOOP_STEP","ELVIS_OPERATOR","FOR_LOOP_RANGE","SECONDARY_CONSTRUCTOR","JAVA_EQUALITY","COMPANION_OBJECT","HAS_COMPANION_OBJECT","NULLABLE_TYPE","WHEN_STATEMENT","EXPLICIT_TYPE","DATA_CLASS","OPEN_CLASS","OPEN_METHOD","COLLECTION_INDEXING","MULTILEVEL_COLLECTION_INDEXING","SINGLETON","FUNCTIONAL_INTERFACE","ANONYMOUS_FUNCTION","IF_EXPRESSIONS","TRY_EXPRESSIONS","WHEN_EXPRESSIONS","WHEN_ENTRY","LAST_WHEN_ENTRY","SWITCH_EXPRESSION","METHOD_CALL"]),t.LocatedFeature=(0,i.Record)({feature:t.Feature,location:t.Location}),t.FeatureValue=(0,i.Record)({featureMap:(0,i.Dictionary)(i.Number,t.Feature),featureList:(0,i.Array)(t.LocatedFeature),importList:(0,i.Array)(i.String),typeList:(0,i.Array)(i.String),identifierList:(0,i.Array)(i.String),dottedMethodList:(0,i.Array)(i.String)}).And((0,i.Partial)({methodList:(0,i.Array)(i.String)})),t.FlatClassFeatures=(0,i.Record)({name:i.String,path:i.String,features:t.FeatureValue}).And((0,i.Partial)({range:t.SourceRange})),t.FlatMethodFeatures=t.FlatClassFeatures,t.FlatUnitFeatures=(0,i.Record)({name:i.String,path:i.String,range:t.SourceRange,features:t.FeatureValue}),t.FlatFeaturesResult=(0,i.Record)({source:i.String,unit:t.FlatUnitFeatures,classes:(0,i.Array)(t.FlatClassFeatures),methods:(0,i.Array)(t.FlatMethodFeatures)}),t.FlatFeaturesResults=(0,i.Record)({results:(0,i.Array)(t.FlatFeaturesResult),allFeatures:(0,i.Dictionary)(i.String)}),t.MutationLocation=(0,i.Record)({start:i.Number,end:i.Number,line:i.String,startLine:i.Number,endLine:i.Number}),t.AppliedMutation=(0,i.Record)({mutationType:i.String,location:t.MutationLocation,original:i.String,mutated:i.String,linesChanged:i.Number}),t.MutatedSource=(0,i.Record)({mutatedSource:i.String,mutatedSources:(0,i.Dictionary)(i.String),mutation:t.AppliedMutation}),t.MutationsResults=(0,i.Record)({source:(0,i.Dictionary)(i.String),mutatedSources:(0,i.Array)(t.MutatedSource)}),t.DisassembleResults=(0,i.Record)({disassemblies:(0,i.Dictionary)(i.String,i.String)}),t.ThrownException=(0,i.Record)({klass:i.String,stacktrace:i.String}).And((0,i.Partial)({message:i.String})),t.Console=(0,i.Union)((0,i.Literal)("STDOUT"),(0,i.Literal)("STDERR")),t.OutputLine=(0,i.Record)({console:t.Console,line:i.String,timestamp:i.String.withConstraint(e=>!isNaN(Date.parse(e)))}).And((0,i.Partial)({thread:i.Number})),t.PermissionRequest=(0,i.Record)({permission:t.Permission,granted:i.Boolean}),t.KILL_REASONS={massiveAllocation:"too large single allocation",exceededAllocationLimit:"exceeded total memory allocation limit",exceededLineLimit:"exceeded total line count limit"},t.SourceTaskResults=(0,i.Record)({klass:i.String,method:i.String,timeout:i.Boolean,outputLines:(0,i.Array)(t.OutputLine),permissionRequests:(0,i.Array)(t.PermissionRequest),interval:t.Interval,executionInterval:t.Interval,truncatedLines:i.Number}).And((0,i.Partial)({returned:i.String,threw:t.ThrownException,killReason:i.String})),t.ContainerExecutionResults=(0,i.Record)({klass:i.String,method:i.String,timeout:i.Boolean,outputLines:(0,i.Array)(t.OutputLine),interval:t.Interval,executionInterval:t.Interval,truncatedLines:i.Number}).And((0,i.Partial)({exitcode:i.Number})),t.CompletedTasks=(0,i.Partial)({snippet:t.Snippet,template:t.TemplatedSourceResult,compilation:t.CompiledSourceResult,kompilation:t.CompiledSourceResult,checkstyle:t.CheckstyleResults,ktlint:t.KtlintResults,complexity:t.FlatComplexityResults,features:t.FlatFeaturesResults,execution:t.SourceTaskResults,cexecution:t.ContainerExecutionResults,mutations:t.MutationsResults,disassemble:t.DisassembleResults}),t.TemplatingError=(0,i.Record)({name:i.String,line:i.Number,column:i.Number,message:i.String}),t.TemplatingFailed=(0,i.Record)({errors:(0,i.Array)(t.TemplatingError)}),t.SnippetTransformationError=(0,i.Record)({line:i.Number,column:i.Number,message:i.String}),t.SnippetTransformationFailed=(0,i.Record)({errors:(0,i.Array)(t.SnippetTransformationError)}),t.CompilationError=(0,i.Record)({message:i.String}).And((0,i.Partial)({location:t.SourceLocation})),t.CompilationFailed=(0,i.Record)({errors:(0,i.Array)(t.CompilationError)}),t.CheckstyleFailed=(0,i.Record)({errors:(0,i.Array)(t.CheckstyleError)}),t.KtlintFailed=(0,i.Record)({errors:(0,i.Array)(t.KtlintError)}),t.SourceError=(0,i.Record)({message:i.String}).And((0,i.Partial)({location:t.SourceLocation})),t.ComplexityFailed=(0,i.Record)({errors:(0,i.Array)(t.SourceError)}),t.FeaturesFailed=(0,i.Record)({errors:(0,i.Array)(t.SourceError)}),t.MutationsFailed=(0,i.Record)({errors:(0,i.Array)(t.SourceError)}),t.DisassembleFailed=(0,i.Record)({message:i.String}),t.ExecutionFailedResult=(0,i.Partial)({classNotFound:i.String,methodNotFound:i.String}),t.FailedTasks=(0,i.Partial)({template:t.TemplatingFailed,snippet:t.SnippetTransformationFailed,compilation:t.CompilationFailed,kompilation:t.CompilationFailed,checkstyle:t.CheckstyleFailed,ktlint:t.KtlintFailed,complexity:t.ComplexityFailed,execution:t.ExecutionFailedResult,cexecution:t.ExecutionFailedResult,features:t.FeaturesFailed,mutations:t.MutationsFailed,disassemble:t.DisassembleFailed}),t.Response=(0,i.Record)({request:t.Request,status:t.ServerStatus,completed:t.CompletedTasks,completedTasks:(0,i.Array)(t.Task),failed:t.FailedTasks,failedTasks:(0,i.Array)(t.Task),interval:t.Interval}).And((0,i.Partial)({email:i.String,audience:(0,i.Array)(i.String)}))},6876:function(e,t,r){"use strict";var i,n=this&&this.__createBinding||(Object.create?function(e,t,r,i){void 0===i&&(i=r);var n=Object.getOwnPropertyDescriptor(t,r);(!n||("get"in n?!t.__esModule:n.writable||n.configurable))&&(n={enumerable:!0,get:function(){return t[r]}}),Object.defineProperty(e,i,n)}:function(e,t,r,i){void 0===i&&(i=r),e[i]=t[r]}),a=this&&this.__setModuleDefault||(Object.create?function(e,t){Object.defineProperty(e,"default",{enumerable:!0,value:t})}:function(e,t){e.default=t}),o=this&&this.__importStar||(i=function(e){return(i=Object.getOwnPropertyNames||function(e){var t=[];for(var r in e)Object.prototype.hasOwnProperty.call(e,r)&&(t[t.length]=r);return t})(e)},function(e){if(e&&e.__esModule)return e;var t={};if(null!=e)for(var r=i(e),o=0;o<r.length;o++)"default"!==r[o]&&n(t,e,r[o]);return a(t,e),t}),l=this&&this.__awaiter||function(e,t,r,i){return new(r||(r=Promise))(function(n,a){function o(e){try{s(i.next(e))}catch(e){a(e)}}function l(e){try{s(i.throw(e))}catch(e){a(e)}}function s(e){var t;e.done?n(e.value):((t=e.value)instanceof r?t:new r(function(e){e(t)})).then(o,l)}s((i=i.apply(e,t||[])).next())})};Object.defineProperty(t,"__esModule",{value:!0}),t.JeedContext=t.useJeed=t.JeedProvider=void 0;let s=r(2646),u=o(r(5834));t.JeedProvider=({googleToken:e,server:r,children:i})=>{let[n,a]=(0,u.useState)(void 0);(0,u.useEffect)(()=>{fetch(r).then(e=>e.json()).then(e=>a(e.status)).catch(()=>a(void 0))},[r]);let o=(0,u.useCallback)((t,...i)=>l(void 0,[t,...i],void 0,function*(t,i=!1){t=i?s.Request.check(t):t;let n=yield fetch(r,{method:"post",body:JSON.stringify(t),headers:Object.assign({"Content-Type":"application/json"},e?{"google-token":e}:null),credentials:"include"}).then(e=>l(void 0,void 0,void 0,function*(){if(200===e.status){let t=yield e.json();return a(t.status),t}throw yield e.text()})).catch(e=>{throw a(void 0),e});return i?s.Response.check(n):n}),[e,r]);return u.default.createElement(t.JeedContext.Provider,{value:{available:!0,status:n,connected:void 0!==n,run:o}},i)},t.useJeed=()=>(0,u.useContext)(t.JeedContext),t.JeedContext=u.default.createContext({available:!1,connected:!1,status:void 0,run:()=>{throw Error("Jeed Context not available")}})},8002:(e,t,r)=>{(window.__NEXT_P=window.__NEXT_P||[]).push(["/",function(){return r(9731)}])},9731:(e,t,r)=>{"use strict";r.r(t),r.d(t,{default:()=>A});var i=r(6514),n=r(9860),a=r(6876),o=r(2646),l=r(3469),s=r.n(l),u=r(5834);let c=s()(()=>Promise.all([r.e(185),r.e(976)]).then(r.bind(r,2976)),{loadableGenerated:{webpack:()=>[2976]},ssr:!1}),d='\n// Java Snippet Mode\n\n// Execution starts at the top level\nSystem.out.println("Hello, Java!");\n\n// Loose code and method definitions supported\nint i = 0;\nSystem.out.println(i);\nint addOne(int value) {\n  return value + 1;\n}\nSystem.out.println(addOne(2));\n\n// Modern Java features like records are supported\nrecord Person(String name, int age) {}\nSystem.out.println(new Person("Geoffrey", 42));\n'.trim(),E='\n// Kotlin Snippet Mode\n\n// Execution starts at the top level\nprintln("Hello, Kotlin!")\n\n// Loose code and method definitions supported\nval i = 0\nprintln(i)\nfun addOne(value: Int) = value + 1\nprintln(addOne(2))\n\n// All Kotlin features are supported\ndata class Person(val name: String, val age: Int)\nprintln(Person("Geoffrey", 42))\n'.trim(),S='\n// Java Source Mode\n\n// Execution starts in Example.main, but this is configurable\npublic class Example {\n  public static void main() {\n    System.out.println("Hello, Java!");\n  }\n}\n'.trim(),m='\n// Kotlin Source Mode\n\n// Execution starts in the top-level main method, but this is configurable\nfun main() {\n  println("Hello, Kotlin!")\n}\n'.trim(),L=()=>{var e,t,r,l,s,L;let[A,T]=(0,u.useState)(""),[p,R]=(0,u.useState)("java"),[N,O]=(0,u.useState)(!0),[_,C]=(0,u.useState)(),{run:I}=(0,a.useJeed)(),g=(0,u.useRef)(null),[h,v]=(0,u.useState)(!1),[F,f]=(0,u.useState)(!1),y=(0,u.useCallback)(async e=>{let t;if(!g.current)return;let r=g.current.getValue();if(""===r.trim()){C(void 0);return}let i={snippet:{indent:2}};if("run"===e)t="java"===p?["compile","execute"]:["kompile","execute"];else if("lint"===e)t="java"===p?["checkstyle"]:["ktlint"];else if("complexity"===e)t=["complexity"];else if("features"===e)t=["features"];else if("disassemble"===e)t="java"===p?["compile","disassemble"]:["kompile","disassemble"];else throw Error(p);t.includes("checkstyle")&&(i.snippet.indent=2,i.checkstyle={failOnError:!0}),t.includes("ktlint")&&(i.ktlint={failOnError:!0}),t.includes("disassemble")&&t.includes("compile")&&(i.compilation={parameters:!0,debugInfo:!0}),"kotlin"===p&&(i.snippet.fileType="KOTLIN");let n={label:"demo",tasks:t,...N&&{snippet:r},...!N&&{sources:[{path:"Example.".concat("java"===p?"java":"kt"),contents:r}]},arguments:i};try{let e=await I(n,!0);C({response:e})}catch(e){C({error:e})}},[p,N,I]),P=(0,u.useMemo)(()=>(null==_?void 0:_.response)?{total:(0,o.intervalDuration)(_.response.interval),..._.response.completed.compilation&&{compilation:(0,o.intervalDuration)(_.response.completed.compilation.interval)},..._.response.completed.kompilation&&{compilation:(0,o.intervalDuration)(_.response.completed.kompilation.interval)},..._.response.completed.execution&&{execution:(0,o.intervalDuration)(_.response.completed.execution.interval)}}:void 0,[_]),x=(0,u.useMemo)(()=>(null==_?void 0:_.response)?(0,n.cX)(_.response):(null==_?void 0:_.error)?{output:null==_?void 0:_.error,level:"error"}:void 0,[_]),M=(0,u.useMemo)(()=>[{name:"gotoline",exec:()=>!1,bindKey:{win:"",mac:""}},{name:"run",bindKey:{win:"Ctrl-Enter",mac:"Ctrl-Enter"},exec:()=>y("run"),readOnly:!0},{name:"close",bindKey:{win:"Esc",mac:"Esc"},exec:()=>C(void 0),readOnly:!0}],[y]);return(0,u.useEffect)(()=>{M.forEach(e=>{var t;g.current&&(null===(t=g.current)||void 0===t||t.commands.addCommand(e))})},[M]),(0,u.useEffect)(()=>{"java"===p?T(N?d:S):T(N?E:m),C(void 0)},[p,N]),(0,i.jsxs)(i.Fragment,{children:[(0,i.jsx)(c,{mode:h?p:"",theme:"github",width:"100%",height:"16rem",minLines:16,maxLines:1/0,value:A,showPrintMargin:!1,onBeforeLoad:e=>{e.config.set("basePath","https://cdn.jsdelivr.net/npm/ace-builds@".concat(e.version,"/src-min-noconflict")),e.config.set("modePath","https://cdn.jsdelivr.net/npm/ace-builds@".concat(e.version,"/src-min-noconflict")),e.config.set("themePath","https://cdn.jsdelivr.net/npm/ace-builds@".concat(e.version,"/src-min-noconflict"))},onLoad:e=>{g.current=e,v(!0)},onChange:T,commands:M,setOptions:{useSoftTabs:!0,tabSize:2}}),(0,i.jsxs)("div",{style:{marginTop:8},children:[(0,i.jsx)("button",{onClick:()=>{y("run")},style:{marginRight:8},children:"Run"}),(0,i.jsx)("button",{onClick:()=>{y("lint")},style:{marginRight:8},children:"java"===p?"checkstyle":"ktlint"}),(0,i.jsx)("button",{onClick:()=>{y("complexity")},style:{marginRight:8},children:"Complexity"}),(0,i.jsx)("button",{onClick:()=>{y("features")},style:{marginRight:8},children:"Features"}),(0,i.jsx)("button",{onClick:()=>{y("disassemble")},style:{marginRight:8},children:"Disassembly"}),(0,i.jsxs)("div",{style:{float:"right"},children:[(0,i.jsx)("button",{style:{marginRight:8},onClick:()=>O(!N),children:N?"Source":"Snippet"}),(0,i.jsx)("button",{onClick:()=>"java"===p?R("kotlin"):R("java"),children:"java"===p?"Kotlin":"Java"})]})]}),void 0!==P&&(0,i.jsxs)("div",{style:{display:"flex",flexDirection:"row"},children:[(0,i.jsxs)("div",{children:["Total: ",P.total,"ms"]}),void 0!==P.compilation&&(0,i.jsxs)("div",{style:{marginLeft:8},children:["Compilation: ",P.compilation,"ms",((null==_?void 0:null===(t=_.response)||void 0===t?void 0:null===(e=t.completed.compilation)||void 0===e?void 0:e.cached)||(null==_?void 0:null===(l=_.response)||void 0===l?void 0:null===(r=l.completed.kompilation)||void 0===r?void 0:r.cached))&&(0,i.jsx)("span",{children:" (Cached)"})]}),void 0!==P.execution&&(0,i.jsxs)("div",{style:{marginLeft:8},children:["Execution: ",P.execution,"ms"]})]}),void 0!==x&&(0,i.jsxs)("div",{style:{marginTop:8},children:[(0,i.jsx)("p",{children:"Output processed to mimic terminal output:"}),(0,i.jsx)("div",{className:"output",children:(0,i.jsx)("span",{className:x.level,style:(null==_?void 0:null===(L=_.response)||void 0===L?void 0:null===(s=L.completed)||void 0===s?void 0:s.disassemble)?{whiteSpace:"pre"}:{},children:x.output})})]}),(null==_?void 0:_.response)&&(0,i.jsxs)("div",{style:{marginTop:8},children:[(0,i.jsx)("p",{children:"Full server response object containing detailed result information."}),(0,i.jsx)(c,{readOnly:!0,theme:"github",mode:F?"json":"",height:"32rem",width:"100%",showPrintMargin:!1,value:JSON.stringify(_.response,null,2),onLoad:()=>{f(!0)}})]})]})};function A(){return(0,i.jsxs)(a.JeedProvider,{server:"https://cloud.cs124.org/jeed",children:[(0,i.jsx)("h2",{children:"Jeed Demo"}),(0,i.jsxs)("div",{style:{marginBottom:8},children:[(0,i.jsxs)("p",{children:[(0,i.jsx)("a",{href:"https://github.com/cs125-illinois/jeed",children:"Jeed"})," is a fast Java and Kotlin execution and analysis toolkit. It compiles and safely executes Java and Kotlin code up to 100 times faster than using a container, allowing a small number of backend servers to easily support a large amount of interactive use."]}),(0,i.jsxs)("p",{children:["Jeed can also perform a variety of analysis tasks, including linting (",(0,i.jsx)("kbd",{children:"checkstyle"})," and"," ",(0,i.jsx)("kbd",{children:"ktlint"}),"), cyclomatic complexity analysis, and language feature analysis (Java only currently). It also supports ",(0,i.jsx)("em",{children:"snippet mode"}),", a relaxed Java and Kotlin syntax that allows top-level method definitions and loose code."]}),(0,i.jsx)("p",{children:"Use the demo below to explore Jeed's features."})]}),(0,i.jsx)(L,{})]})}},9860:(e,t,r)=>{"use strict";t.cX=function(e){var t,r,a,o,l,s,u,c,d,E,S;let{request:m}=e;if(e.failed.snippet){let t=e.failed.snippet.errors.map(({line:e,column:t,message:r})=>{let i=n(m,e);return`Line ${e}: error: ${r}
  ${i?i+"\n"+Array(t).join(" ")+"^":""}`}).join("\n"),r=Object.keys(e.failed.snippet.errors).length;return{output:`${t}
  ${r} error${r>1?"s":""}`,level:"error"}}if(e.failed.compilation||e.failed.kompilation){let i=(null===(t=e.failed.compilation||e.failed.kompilation)||void 0===t?void 0:t.errors.map(e=>{let{location:t,message:r}=e;if(!t)return r;{let{source:e,line:i,column:a}=t,o=n(m,i,e),l=r.split("\n").slice(0,1).join(),s=r.split("\n").slice(1).filter(t=>!(""===e&&t.trim().startsWith("location: class"))).join("\n");return`${""===e?"Line ":`${e}:`}${i}: error: ${l}
${o?o+"\n"+Array(a).join(" ")+"^":""}${s?"\n"+s:""}`}}).join("\n"))||"",a=Object.keys((null===(r=e.failed.compilation||e.failed.kompilation)||void 0===r?void 0:r.errors)||{}).length;return{output:`${i}
  ${a} error${a>1?"s":""}`,level:"error"}}if(e.failed.checkstyle){let t=(null===(a=e.failed.checkstyle)||void 0===a?void 0:a.errors.map(({location:{source:e,line:t},message:r})=>`${""===e?"Line ":`${e}:`}${t}: checkstyle error: ${r}`).join("\n"))||"",r=Object.keys((null===(o=e.failed.checkstyle)||void 0===o?void 0:o.errors)||{}).length;return{output:`${t}
  ${r} error${r>1?"s":""}`,level:"error"}}if(e.failed.ktlint){let t=(null===(l=e.failed.ktlint)||void 0===l?void 0:l.errors.map(({location:{source:e,line:t},detail:r})=>`${""===e?"Line ":`${e}:`}${t}: ktlint error: ${r}`).join("\n"))||"",r=Object.keys((null===(s=e.failed.ktlint)||void 0===s?void 0:s.errors)||{}).length;return{output:`${t}
  ${r} error${r>1?"s":""}`,level:"error"}}if(e.failed.disassemble)return{output:e.failed.disassemble.message,level:"error"};else if(e.failed.execution||e.failed.cexecution){let t=e.failed.execution||e.failed.cexecution;return(null==t?void 0:t.classNotFound)?{output:`Error: could not find class ${null==t?void 0:t.classNotFound}`,level:"error"}:(null==t?void 0:t.methodNotFound)?{output:`Error: could not find method ${null==t?void 0:t.methodNotFound}`,level:"error"}:{output:"Something unexpected went wrong. Please report a bug.",level:"error"}}if(0===Object.keys(e.failed).length){if(e.completed.execution||e.completed.cexecution){let t="success",r=e.completed.execution||e.completed.cexecution,n=(null==r?void 0:r.outputLines)?r.outputLines.length>0?r.outputLines.map(({line:e})=>e):(null===(u=e.completed.execution)||void 0===u?void 0:u.threw)?[]:["(Completed without output)"]:[];return(null===(c=e.completed.execution)||void 0===c?void 0:c.killReason)?(t="error",n.push(`Execution did not complete: ${null!==(d=i.KILL_REASONS[e.completed.execution.killReason])&&void 0!==d?d:e.completed.execution.killReason}.`)):(null===(E=e.completed.execution)||void 0===E?void 0:E.threw)?(t="error",n.push(`Threw an exception: ${null===(S=e.completed.execution)||void 0===S?void 0:S.threw.stacktrace}`)):(null==r?void 0:r.timeout)&&(t="error",n.push("(Program timed out)")),(null==r?void 0:r.truncatedLines)&&(t="warning",n.push(`(${null==r?void 0:r.truncatedLines} lines were truncated)`)),{output:n.join("\n"),level:t}}if(e.completed.checkstyle)return{output:"No checkstyle errors found",level:"success"};if(e.completed.ktlint)return{output:"No ktlint errors found",level:"success"};else if(e.completed.complexity){let t=e.completed.complexity.results,r=[];for(let e of t){let t=e.classes.map(({complexity:e})=>e).reduce((e,t)=>t+e,0),i=""===e.source?"Entire snippet":e.source;for(let n of(r.push(`${i} has complexity ${t}`),e.classes))""!==n.name&&r.push(`  Class ${n.name} has complexity ${n.complexity}`);for(let t of e.methods){let e=""===t.name?"Loose code":`Method ${t.name}`;r.push(`  ${e} has complexity ${t.complexity}`)}}return{output:r.join("\n"),level:"success"}}else if(e.completed.disassemble){let t=e.completed.disassemble.disassemblies,r=[];for(let e of Object.keys(t).sort())r.push(t[e]);return{output:r.join("\n\n\n"),level:"success"}}if(e.completed.features){let{results:t,allFeatures:r}=e.completed.features,i=[];for(let e of t){let t={};for(let r of e.classes)for(let e of Object.keys(r.features.featureMap))t[e]=!0;let n=""===e.source?"Entire snippet":e.source;i.push(`${n} uses features ${Object.keys(t).map(e=>r[e]).sort()}`)}return{output:i.join("\n"),level:"success"}}}throw Error("Can't generate output for this result")};let i=r(2646);function n(e,t,r){if(e.snippet)return e.snippet.split("\n")[t-1];for(let{path:i,contents:n}of e.sources||[])if(r===i||r===`/${i}`)return n.split("\n")[t-1];throw Error(`Couldn't find line ${t} in source ${r}`)}}},e=>{var t=t=>e(e.s=t);e.O(0,[726,636,593,792],()=>t(8002)),_N_E=e.O()}]);