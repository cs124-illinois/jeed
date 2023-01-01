(self.webpackChunk_N_E=self.webpackChunk_N_E||[]).push([[405],{9536:function(e,t,i){(window.__NEXT_P=window.__NEXT_P||[]).push(["/",function(){return i(5979)}])},5979:function(e,t,i){"use strict";i.r(t),i.d(t,{default:function(){return R}});var r=i(4246),n=i(1686),o=i(2547),l=i(8741),a=i(6695),s=i(5218),u=i.n(s),c=i(7378),d=i(7061);let m=u()(()=>Promise.all([i.e(90),i.e(329)]).then(i.bind(i,2329)),{loadableGenerated:{webpack:()=>[2329]},ssr:!1}),p='\n// Java Snippet Mode\n\n// Execution starts at the top level\nSystem.out.println("Hello, Java!");\n\n// Loose code and method definitions supported\nint i = 0;\nSystem.out.println(i);\nint addOne(int value) {\n  return value + 1;\n}\nSystem.out.println(addOne(2));\n\n// Modern Java features like records are supported\nrecord Person(String name, int age) {}\nSystem.out.println(new Person("Geoffrey", 42));\n'.trim(),S='\n// Kotlin Snippet Mode\n\n// Execution starts at the top level\nprintln("Hello, Kotlin!")\n\n// Loose code and method definitions supported\nval i = 0\nprintln(i)\nfun addOne(value: Int) = value + 1\nprintln(addOne(2))\n\n// All Kotlin features are supported\ndata class Person(val name: String, val age: Int)\nprintln(Person("Geoffrey", 42))\n'.trim(),g='\n// Java Source Mode\n\n// Execution starts in Example.main, but this is configurable\npublic class Example {\n  public static void main() {\n    System.out.println("Hello, Java!");\n  }\n}\n'.trim(),h='\n// Kotlin Source Mode\n\n// Execution starts in the top-level main method, but this is configurable\nfun main() {\n  println("Hello, Kotlin!")\n}\n'.trim(),L=()=>{let{isSignedIn:e,auth:t,ready:i}=(0,a.useGoogleLogin)();return i?(0,r.jsx)("button",{onClick:()=>e?null==t?void 0:t.signOut():null==t?void 0:t.signIn(),children:e?"Signout":"Signin"}):null},v=()=>{var e,t,i,a,s,u;let[d,L]=(0,c.useState)(""),[v,R]=(0,c.useState)("java"),[E,f]=(0,c.useState)(!0),[y,A]=(0,c.useState)(),{run:C}=(0,o.useJeed)(),x=(0,c.useRef)(),T=(0,c.useCallback)(async e=>{let t;if(!x.current)return;let i=x.current.getValue();if(""===i.trim()){A(void 0);return}let r={snippet:{indent:2}};if("run"===e)t="java"===v?["compile","execute"]:["kompile","execute"];else if("lint"===e)t="java"===v?["checkstyle"]:["ktlint"];else if("complexity"===e)t=["complexity"];else if("features"===e){if("java"!==v)throw Error("features not yet supported for Kotlin");t=["features"]}else if("disassemble"===e)t="java"===v?["compile","disassemble"]:["kompile","disassemble"];else throw Error(v);t.includes("checkstyle")&&(r.snippet.indent=2,r.checkstyle={failOnError:!0}),t.includes("ktlint")&&(r.ktlint={failOnError:!0}),t.includes("disassemble")&&t.includes("compile")&&(r.compilation={parameters:!0,debugInfo:!0}),"kotlin"===v&&(r.snippet.fileType="KOTLIN");let n={label:"demo",tasks:t,...E&&{snippet:i},...!E&&{sources:[{path:"Example.".concat("java"===v?"java":"kt"),contents:i}]},arguments:r};try{let o=await C(n,!0);A({response:o})}catch(l){A({error:l})}},[v,E,C]),N=(0,c.useMemo)(()=>(null==y?void 0:y.response)?{total:(0,l.intervalDuration)(y.response.interval),...y.response.completed.compilation&&{compilation:(0,l.intervalDuration)(y.response.completed.compilation.interval)},...y.response.completed.kompilation&&{compilation:(0,l.intervalDuration)(y.response.completed.kompilation.interval)},...y.response.completed.execution&&{execution:(0,l.intervalDuration)(y.response.completed.execution.interval)}}:void 0,[y]),k=(0,c.useMemo)(()=>(null==y?void 0:y.response)?(0,n.P7)(y.response):(null==y?void 0:y.error)?{output:null==y?void 0:y.error,level:"error"}:void 0,[y]),b=(0,c.useMemo)(()=>[{name:"gotoline",exec:()=>!1,bindKey:{win:"",mac:""}},{name:"run",bindKey:{win:"Ctrl-Enter",mac:"Ctrl-Enter"},exec:()=>T("run"),readOnly:!0},{name:"close",bindKey:{win:"Esc",mac:"Esc"},exec:()=>A(void 0),readOnly:!0}],[T]);return(0,c.useEffect)(()=>{b.forEach(e=>{var t;x.current&&(null===(t=x.current)||void 0===t||t.commands.addCommand(e))})},[b]),(0,c.useEffect)(()=>{"java"===v?L(E?p:g):L(E?S:h),A(void 0)},[v,E]),(0,r.jsxs)(r.Fragment,{children:[(0,r.jsx)(m,{mode:v,theme:"github",width:"100%",height:"16rem",minLines:16,maxLines:1/0,value:d,showPrintMargin:!1,onBeforeLoad:e=>{e.config.set("basePath","https://cdn.jsdelivr.net/npm/ace-builds@".concat(e.version,"/src-min-noconflict"))},onLoad:e=>{x.current=e},onChange:L,commands:b,setOptions:{tabSize:2},editorProps:{}}),(0,r.jsxs)("div",{style:{marginTop:8},children:[(0,r.jsx)("button",{onClick:()=>{T("run")},style:{marginRight:8},children:"Run"}),(0,r.jsx)("button",{onClick:()=>{T("lint")},style:{marginRight:8},children:"java"===v?"checkstyle":"ktlint"}),(0,r.jsx)("button",{onClick:()=>{T("complexity")},style:{marginRight:8},children:"Complexity"}),"java"===v&&(0,r.jsx)("button",{onClick:()=>{T("features")},style:{marginRight:8},children:"Features"}),(0,r.jsx)("button",{onClick:()=>{T("disassemble")},style:{marginRight:8},children:"Disassembly"}),(0,r.jsxs)("div",{style:{float:"right"},children:[(0,r.jsx)("button",{style:{marginRight:8},onClick:()=>f(!E),children:E?"Source":"Snippet"}),(0,r.jsx)("button",{onClick:()=>"java"===v?R("kotlin"):R("java"),children:"java"===v?"Kotlin":"Java"})]})]}),void 0!==N&&(0,r.jsxs)("div",{style:{display:"flex",flexDirection:"row"},children:[(0,r.jsxs)("div",{children:["Total: ",N.total,"ms"]}),void 0!==N.compilation&&(0,r.jsxs)("div",{style:{marginLeft:8},children:["Compilation: ",N.compilation,"ms",((null===(e=null==y?void 0:null===(t=y.response)||void 0===t?void 0:t.completed.compilation)||void 0===e?void 0:e.cached)||(null===(i=null==y?void 0:null===(a=y.response)||void 0===a?void 0:a.completed.kompilation)||void 0===i?void 0:i.cached))&&(0,r.jsx)("span",{children:" (Cached)"})]}),void 0!==N.execution&&(0,r.jsxs)("div",{style:{marginLeft:8},children:["Execution: ",N.execution,"ms"]})]}),void 0!==k&&(0,r.jsxs)("div",{style:{marginTop:8},children:[(0,r.jsx)("p",{children:"Output processed to mimic terminal output:"}),(0,r.jsx)("div",{className:"output",children:(0,r.jsx)("span",{className:k.level,style:(null==y?void 0:null===(s=y.response)||void 0===s?void 0:null===(u=s.completed)||void 0===u?void 0:u.disassemble)?{whiteSpace:"pre"}:{},children:k.output})})]}),(null==y?void 0:y.response)&&(0,r.jsxs)("div",{style:{marginTop:8},children:[(0,r.jsx)("p",{children:"Full server response object containing detailed result information."}),(0,r.jsx)(m,{readOnly:!0,theme:"github",mode:"json",height:"32rem",width:"100%",showPrintMargin:!1,value:JSON.stringify(y.response,null,2),setOptions:{},editorProps:{}})]})]})};function R(){return(0,r.jsx)(a.GoogleLoginProvider,{clientConfig:{client_id:d.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID},children:(0,r.jsx)(a.WithGoogleTokens,{children:e=>{let{idToken:t}=e;return(0,r.jsxs)(o.JeedProvider,{googleToken:t,server:d.env.NEXT_PUBLIC_JEED_SERVER,children:[(0,r.jsx)("h2",{children:"Jeed Demo"}),(0,r.jsx)("div",{style:{marginBottom:8},children:(0,r.jsx)(L,{})}),(0,r.jsxs)("div",{style:{marginBottom:8},children:[(0,r.jsxs)("p",{children:[(0,r.jsx)("a",{href:"https://github.com/cs125-illinois/jeed",children:"Jeed"})," is a fast Java and Kotlin execution and analysis toolkit. It compiles and safely executes Java and Kotlin code up to 100 times faster than using a container, allowing a small number of backend servers to easily support a large amount of interactive use."]}),(0,r.jsxs)("p",{children:["Jeed can also perform a variety of analysis tasks, including linting (",(0,r.jsx)("kbd",{children:"checkstyle"})," and"," ",(0,r.jsx)("kbd",{children:"ktlint"}),"), cyclomatic complexity analysis, and language feature analysis (Java only currently). It also supports ",(0,r.jsx)("em",{children:"snippet mode"}),", a relaxed Java and Kotlin syntax that allows top-level method definitions and loose code."]}),(0,r.jsx)("p",{children:"Use the demo below to explore Jeed's features."})]}),(0,r.jsx)(v,{})]})}})})}},1686:function(e,t,i){t.P7=void 0;let r=i(8741);function n(e,t,i){if(e.snippet)return e.snippet.split("\n")[t-1];for(let{path:r,contents:n}of e.sources||[])if(i===r||i===`/${r}`)return n.split("\n")[t-1];throw Error(`Couldn't find line ${t} in source ${i}`)}t.P7=function(e){var t,i,o,l,a,s,u,c,d,m,p;let{request:S}=e;if(e.failed.snippet){let g=e.failed.snippet.errors.map(({line:e,column:t,message:i})=>{let r=n(S,e);return`Line ${e}: error: ${i}
  ${r?r+"\n"+Array(t).join(" ")+"^":""}`}).join("\n"),h=Object.keys(e.failed.snippet.errors).length;return{output:`${g}
  ${h} error${h>1?"s":""}`,level:"error"}}if(e.failed.compilation||e.failed.kompilation){let L=(null===(t=e.failed.compilation||e.failed.kompilation)||void 0===t?void 0:t.errors.map(e=>{let{location:t,message:i}=e;if(!t)return i;{let{source:r,line:o,column:l}=t,a=n(S,o,r),s=i.split("\n").slice(0,1).join(),u=i.split("\n").slice(1).filter(e=>!(""===r&&e.trim().startsWith("location: class"))).join("\n");return`${""===r?"Line ":`${r}:`}${o}: error: ${s}
${a?a+"\n"+Array(l).join(" ")+"^":""}${u?"\n"+u:""}`}}).join("\n"))||"",v=Object.keys((null===(i=e.failed.compilation||e.failed.kompilation)||void 0===i?void 0:i.errors)||{}).length;return{output:`${L}
  ${v} error${v>1?"s":""}`,level:"error"}}if(e.failed.checkstyle){let R=(null===(o=e.failed.checkstyle)||void 0===o?void 0:o.errors.map(({location:{source:e,line:t},message:i})=>`${""===e?"Line ":`${e}:`}${t}: checkstyle error: ${i}`).join("\n"))||"",E=Object.keys((null===(l=e.failed.checkstyle)||void 0===l?void 0:l.errors)||{}).length;return{output:`${R}
  ${E} error${E>1?"s":""}`,level:"error"}}if(e.failed.ktlint){let f=(null===(a=e.failed.ktlint)||void 0===a?void 0:a.errors.map(({location:{source:e,line:t},detail:i})=>`${""===e?"Line ":`${e}:`}${t}: ktlint error: ${i}`).join("\n"))||"",y=Object.keys((null===(s=e.failed.ktlint)||void 0===s?void 0:s.errors)||{}).length;return{output:`${f}
  ${y} error${y>1?"s":""}`,level:"error"}}if(e.failed.disassemble)return{output:e.failed.disassemble.message,level:"error"};if(e.failed.execution||e.failed.cexecution){let A=e.failed.execution||e.failed.cexecution;return(null==A?void 0:A.classNotFound)?{output:`Error: could not find class ${null==A?void 0:A.classNotFound}`,level:"error"}:(null==A?void 0:A.methodNotFound)?{output:`Error: could not find method ${null==A?void 0:A.methodNotFound}`,level:"error"}:{output:"Something unexpected went wrong. Please report a bug.",level:"error"}}if(0===Object.keys(e.failed).length){if(e.completed.execution||e.completed.cexecution){let C="success",x=e.completed.execution||e.completed.cexecution,T=(null==x?void 0:x.outputLines)?x.outputLines.length>0?x.outputLines.map(({line:e})=>e):(null===(u=e.completed.execution)||void 0===u?void 0:u.threw)?[]:["(Completed without output)"]:[];return(null===(c=e.completed.execution)||void 0===c?void 0:c.killReason)?(C="error",T.push(`Execution did not complete: ${null!==(d=r.KILL_REASONS[e.completed.execution.killReason])&&void 0!==d?d:e.completed.execution.killReason}.`)):(null===(m=e.completed.execution)||void 0===m?void 0:m.threw)?(C="error",T.push(`Threw an exception: ${null===(p=e.completed.execution)||void 0===p?void 0:p.threw.stacktrace}`)):(null==x?void 0:x.timeout)&&(C="error",T.push("(Program timed out)")),(null==x?void 0:x.truncatedLines)&&(C="warning",T.push(`(${null==x?void 0:x.truncatedLines} lines were truncated)`)),{output:T.join("\n"),level:C}}if(e.completed.checkstyle)return{output:"No checkstyle errors found",level:"success"};if(e.completed.ktlint)return{output:"No ktlint errors found",level:"success"};if(e.completed.complexity){let N=e.completed.complexity.results,k=[];for(let b of N){let F=b.classes.map(({complexity:e})=>e).reduce((e,t)=>t+e,0),O=""===b.source?"Entire snippet":b.source;for(let _ of(k.push(`${O} has complexity ${F}`),b.classes))""!==_.name&&k.push(`  Class ${_.name} has complexity ${_.complexity}`);for(let I of b.methods){let j=""===I.name?"Loose code":`Method ${I.name}`;k.push(`  ${j} has complexity ${I.complexity}`)}}return{output:k.join("\n"),level:"success"}}else if(e.completed.disassemble){let P=e.completed.disassemble.disassemblies,M=[];for(let D of Object.keys(P).sort())M.push(P[D]);return{output:M.join("\n\n\n"),level:"success"}}if(e.completed.features){let{results:w,allFeatures:$}=e.completed.features,B=[];for(let K of w){let J={};for(let H of K.classes)for(let U of Object.keys(H.features.featureMap))J[U]=!0;let G=""===K.source?"Entire snippet":K.source;B.push(`${G} uses features ${Object.keys(J).map(e=>$[e]).sort()}`)}return{output:B.join("\n"),level:"success"}}}throw Error("Can't generate output for this result")}},2547:function(e,t,i){var r=this&&this.__createBinding||(Object.create?function(e,t,i,r){void 0===r&&(r=i);var n=Object.getOwnPropertyDescriptor(t,i);(!n||("get"in n?!t.__esModule:n.writable||n.configurable))&&(n={enumerable:!0,get:function(){return t[i]}}),Object.defineProperty(e,r,n)}:function(e,t,i,r){void 0===r&&(r=i),e[r]=t[i]}),n=this&&this.__setModuleDefault||(Object.create?function(e,t){Object.defineProperty(e,"default",{enumerable:!0,value:t})}:function(e,t){e.default=t}),o=this&&this.__importStar||function(e){if(e&&e.__esModule)return e;var t={};if(null!=e)for(var i in e)"default"!==i&&Object.prototype.hasOwnProperty.call(e,i)&&r(t,e,i);return n(t,e),t},l=this&&this.__awaiter||function(e,t,i,r){return new(i||(i=Promise))(function(n,o){function l(e){try{s(r.next(e))}catch(t){o(t)}}function a(e){try{s(r.throw(e))}catch(t){o(t)}}function s(e){var t;e.done?n(e.value):((t=e.value)instanceof i?t:new i(function(e){e(t)})).then(l,a)}s((r=r.apply(e,t||[])).next())})};Object.defineProperty(t,"__esModule",{value:!0}),t.JeedContext=t.useJeed=t.JeedProvider=void 0;let a=i(8741),s=o(i(7378)),u=({googleToken:e,server:i,children:r})=>{let[n,o]=(0,s.useState)(void 0);(0,s.useEffect)(()=>{fetch(i).then(e=>e.json()).then(e=>o(e.status)).catch(()=>o(void 0))},[i]);let u=(0,s.useCallback)((t,r=!1)=>l(this,void 0,void 0,function*(){t=r?a.Request.check(t):t;let n=yield fetch(i,{method:"post",body:JSON.stringify(t),headers:Object.assign({"Content-Type":"application/json"},e?{"google-token":e}:null),credentials:"include"}).then(e=>l(this,void 0,void 0,function*(){if(200===e.status){let t=yield e.json();return o(t.status),t}throw yield e.text()})).catch(e=>{throw o(void 0),e});return r?a.Response.check(n):n}),[e,i]);return s.default.createElement(t.JeedContext.Provider,{value:{available:!0,status:n,connected:void 0!==n,run:u}},r)};t.JeedProvider=u;let c=()=>(0,s.useContext)(t.JeedContext);t.useJeed=c,t.JeedContext=s.default.createContext({available:!1,connected:!1,status:void 0,run:()=>{throw Error("Jeed Context not available")}})},8741:function(e,t,i){Object.defineProperty(t,"__esModule",{value:!0}),t.SourceTaskResults=t.KILL_REASONS=t.PermissionRequest=t.OutputLine=t.Console=t.ThrownException=t.DisassembleResults=t.MutationsResults=t.MutatedSource=t.AppliedMutation=t.MutationLocation=t.FlatFeaturesResults=t.FlatFeaturesResult=t.FlatMethodFeatures=t.FlatClassFeatures=t.FeatureValue=t.Feature=t.FlatComplexityResults=t.FlatComplexityResult=t.FlatMethodComplexity=t.FlatClassComplexity=t.KtlintResults=t.KtlintError=t.CheckstyleResults=t.CheckstyleError=t.CompiledSourceResult=t.CompilationMessage=t.TemplatedSourceResult=t.Snippet=t.Request=t.TaskArguments=t.MutationsArguments=t.ContainerExecutionArguments=t.SourceExecutionArguments=t.ClassLoaderConfiguration=t.KtLintArguments=t.CheckstyleArguments=t.KompilationArguments=t.CompilationArguments=t.SnippetArguments=t.ServerStatus=t.intervalDuration=t.Interval=t.SourceLocation=t.SourceRange=t.Location=t.FileType=t.Permission=t.FlatSource=t.Task=void 0,t.Response=t.FailedTasks=t.ExecutionFailedResult=t.DisassembleFailed=t.MutationsFailed=t.FeaturesFailed=t.ComplexityFailed=t.SourceError=t.KtlintFailed=t.CheckstyleFailed=t.CompilationFailed=t.CompilationError=t.SnippetTransformationFailed=t.SnippetTransformationError=t.TemplatingFailed=t.TemplatingError=t.CompletedTasks=t.ContainerExecutionResults=void 0;let r=i(4690);t.Task=(0,r.Union)((0,r.Literal)("template"),(0,r.Literal)("snippet"),(0,r.Literal)("compile"),(0,r.Literal)("kompile"),(0,r.Literal)("checkstyle"),(0,r.Literal)("ktlint"),(0,r.Literal)("complexity"),(0,r.Literal)("execute"),(0,r.Literal)("cexecute"),(0,r.Literal)("features"),(0,r.Literal)("mutations"),(0,r.Literal)("disassemble")),t.FlatSource=(0,r.Record)({path:r.String,contents:r.String}),t.Permission=(0,r.Record)({klass:r.String,name:r.String}).And((0,r.Partial)({actions:r.String})),t.FileType=(0,r.Union)((0,r.Literal)("JAVA"),(0,r.Literal)("KOTLIN")),t.Location=(0,r.Record)({line:r.Number,column:r.Number}),t.SourceRange=(0,r.Record)({start:t.Location,end:t.Location}).And((0,r.Partial)({source:r.String})),t.SourceLocation=(0,r.Record)({source:r.String,line:r.Number,column:r.Number}),t.Interval=(0,r.Record)({start:r.String.withConstraint(e=>!isNaN(Date.parse(e))),end:r.String.withConstraint(e=>!isNaN(Date.parse(e)))});let n=e=>new Date(e.end).valueOf()-new Date(e.start).valueOf();t.intervalDuration=n,t.ServerStatus=(0,r.Record)({tasks:(0,r.Array)(t.Task),started:r.String.withConstraint(e=>!isNaN(Date.parse(e))),hostname:r.String,versions:(0,r.Record)({jeed:r.String,server:r.String,compiler:r.String,kompiler:r.String}),counts:(0,r.Record)({submitted:r.Number,completed:r.Number,saved:r.Number}),cache:(0,r.Record)({inUse:r.Boolean,sizeInMB:r.Number,hits:r.Number,misses:r.Number,hitRate:r.Number,evictionCount:r.Number,averageLoadPenalty:r.Number})}).And((0,r.Partial)({lastRequest:r.String.withConstraint(e=>!isNaN(Date.parse(e)))})),t.SnippetArguments=(0,r.Partial)({indent:r.Number,fileType:t.FileType,noEmptyMain:r.Boolean}),t.CompilationArguments=(0,r.Partial)({wError:r.Boolean,XLint:r.String,enablePreview:r.Boolean,useCache:r.Boolean,waitForCache:r.Boolean,parameters:r.Boolean,debugInfo:r.Boolean}),t.KompilationArguments=(0,r.Partial)({verbose:r.Boolean,allWarningsAsErrors:r.Boolean,useCache:r.Boolean,waitForCache:r.Boolean}),t.CheckstyleArguments=(0,r.Partial)({sources:(0,r.Array)(r.String),failOnError:r.Boolean}),t.KtLintArguments=(0,r.Partial)({sources:(0,r.Array)(r.String),failOnError:r.Boolean,indent:r.Number,maxLineLength:r.Number}),t.ClassLoaderConfiguration=(0,r.Partial)({whitelistedClasses:(0,r.Array)(r.String),blacklistedClasses:(0,r.Array)(r.String),unsafeExceptions:(0,r.Array)(r.String),isolatedClasses:(0,r.Array)(r.String)}),t.SourceExecutionArguments=(0,r.Partial)({klass:r.String,method:r.String,timeout:r.Number,permissions:(0,r.Array)(t.Permission),maxExtraThreads:r.Number,maxOutputLines:r.Number,classLoaderConfiguration:t.ClassLoaderConfiguration}),t.ContainerExecutionArguments=(0,r.Partial)({klass:r.String,method:r.String,image:r.String,timeout:r.Number,maxOutputLines:r.Number,containerArguments:r.String}),t.MutationsArguments=(0,r.Partial)({limit:r.Number,suppressWithComments:r.Boolean}),t.TaskArguments=(0,r.Partial)({snippet:t.SnippetArguments,compilation:t.CompilationArguments,kompilation:t.KompilationArguments,checkstyle:t.CheckstyleArguments,ktlint:t.KtLintArguments,execution:t.SourceExecutionArguments,cexecution:t.ContainerExecutionArguments,mutations:t.MutationsArguments}),t.Request=(0,r.Record)({tasks:(0,r.Array)(t.Task),label:r.String}).And((0,r.Partial)({sources:(0,r.Array)(t.FlatSource),templates:(0,r.Array)(t.FlatSource),snippet:r.String,arguments:t.TaskArguments,checkForSnippet:r.Boolean})),t.Snippet=(0,r.Record)({sources:(0,r.Dictionary)(r.String),originalSource:r.String,rewrittenSource:r.String,snippetRange:t.SourceRange,wrappedClassName:r.String,looseCodeMethodName:r.String,fileType:t.FileType}),t.TemplatedSourceResult=(0,r.Record)({sources:(0,r.Dictionary)(r.String),originalSources:(0,r.Dictionary)(r.String)}),t.CompilationMessage=(0,r.Record)({kind:r.String,message:r.String}).And((0,r.Partial)({location:t.SourceLocation})),t.CompiledSourceResult=(0,r.Record)({messages:(0,r.Array)(t.CompilationMessage),compiled:r.String.withConstraint(e=>!isNaN(Date.parse(e))),interval:t.Interval,compilerName:r.String,cached:r.Boolean}),t.CheckstyleError=(0,r.Record)({severity:r.String,location:t.SourceLocation,message:r.String}),t.CheckstyleResults=(0,r.Record)({errors:(0,r.Array)(t.CheckstyleError)}),t.KtlintError=(0,r.Record)({ruleId:r.String,detail:r.String,location:t.SourceLocation}),t.KtlintResults=(0,r.Record)({errors:(0,r.Array)(t.KtlintError)}),t.FlatClassComplexity=(0,r.Record)({name:r.String,path:r.String,range:t.SourceRange,complexity:r.Number}),t.FlatMethodComplexity=t.FlatClassComplexity,t.FlatComplexityResult=(0,r.Record)({source:r.String,classes:(0,r.Array)(t.FlatClassComplexity),methods:(0,r.Array)(t.FlatMethodComplexity)}),t.FlatComplexityResults=(0,r.Record)({results:(0,r.Array)(t.FlatComplexityResult)}),t.Feature=(0,r.Union)((0,r.Literal)("LOCAL_VARIABLE_DECLARATIONS"),(0,r.Literal)("VARIABLE_ASSIGNMENTS"),(0,r.Literal)("VARIABLE_REASSIGNMENTS"),(0,r.Literal)("UNARY_OPERATORS"),(0,r.Literal)("ARITHMETIC_OPERATORS"),(0,r.Literal)("BITWISE_OPERATORS"),(0,r.Literal)("ASSIGNMENT_OPERATORS"),(0,r.Literal)("TERNARY_OPERATOR"),(0,r.Literal)("COMPARISON_OPERATORS"),(0,r.Literal)("LOGICAL_OPERATORS"),(0,r.Literal)("PRIMITIVE_CASTING"),(0,r.Literal)("IF_STATEMENTS"),(0,r.Literal)("ELSE_STATEMENTS"),(0,r.Literal)("ELSE_IF"),(0,r.Literal)("ARRAYS"),(0,r.Literal)("ARRAY_ACCESS"),(0,r.Literal)("ARRAY_LITERAL"),(0,r.Literal)("MULTIDIMENSIONAL_ARRAYS"),(0,r.Literal)("FOR_LOOPS"),(0,r.Literal)("ENHANCED_FOR"),(0,r.Literal)("WHILE_LOOPS"),(0,r.Literal)("DO_WHILE_LOOPS"),(0,r.Literal)("BREAK"),(0,r.Literal)("CONTINUE"),(0,r.Literal)("NESTED_IF"),(0,r.Literal)("NESTED_FOR"),(0,r.Literal)("NESTED_WHILE"),(0,r.Literal)("NESTED_DO_WHILE"),(0,r.Literal)("NESTED_CLASS"),(0,r.Literal)("NESTED_LOOP"),(0,r.Literal)("METHOD"),(0,r.Literal)("RETURN"),(0,r.Literal)("CONSTRUCTOR"),(0,r.Literal)("GETTER"),(0,r.Literal)("SETTER"),(0,r.Literal)("NULL"),(0,r.Literal)("STRING"),(0,r.Literal)("CASTING"),(0,r.Literal)("TYPE_INFERENCE"),(0,r.Literal)("INSTANCEOF"),(0,r.Literal)("CLASS"),(0,r.Literal)("IMPLEMENTS"),(0,r.Literal)("INTERFACE"),(0,r.Literal)("EXTENDS"),(0,r.Literal)("SUPER"),(0,r.Literal)("OVERRIDE"),(0,r.Literal)("TRY_BLOCK"),(0,r.Literal)("FINALLY"),(0,r.Literal)("ASSERT"),(0,r.Literal)("THROW"),(0,r.Literal)("THROWS"),(0,r.Literal)("NEW_KEYWORD"),(0,r.Literal)("THIS"),(0,r.Literal)("REFERENCE_EQUALITY"),(0,r.Literal)("VISIBILITY_MODIFIERS"),(0,r.Literal)("STATIC_METHOD"),(0,r.Literal)("FINAL_METHOD"),(0,r.Literal)("ABSTRACT_METHOD"),(0,r.Literal)("STATIC_FIELD"),(0,r.Literal)("FINAL_FIELD"),(0,r.Literal)("ABSTRACT_FIELD"),(0,r.Literal)("FINAL_CLASS"),(0,r.Literal)("ABSTRACT_CLASS"),(0,r.Literal)("IMPORT"),(0,r.Literal)("ANONYMOUS_CLASSES"),(0,r.Literal)("LAMBDA_EXPRESSIONS"),(0,r.Literal)("GENERIC_CLASS"),(0,r.Literal)("SWITCH"),(0,r.Literal)("STREAM"),(0,r.Literal)("ENUM"),(0,r.Literal)("RECURSION"),(0,r.Literal)("COMPARABLE"),(0,r.Literal)("RECORD"),(0,r.Literal)("BOXING_CLASSES"),(0,r.Literal)("TYPE_PARAMETERS"),(0,r.Literal)("PRINT_STATEMENTS"),(0,r.Literal)("DOT_NOTATION"),(0,r.Literal)("DOTTED_METHOD_CALL"),(0,r.Literal)("DOTTED_VARIABLE_ACCESS"),(0,r.Literal)("NESTED_METHOD"),(0,r.Literal)("JAVA_PRINT_STATEMENTS")),t.FeatureValue=(0,r.Record)({featureMap:(0,r.Dictionary)(r.Number,t.Feature),importList:(0,r.Array)(r.String),typeList:(0,r.Array)(r.String),identifierList:(0,r.Array)(r.String)}),t.FlatClassFeatures=(0,r.Record)({name:r.String,path:r.String,range:t.SourceRange,features:t.FeatureValue}),t.FlatMethodFeatures=t.FlatClassFeatures,t.FlatFeaturesResult=(0,r.Record)({source:r.String,classes:(0,r.Array)(t.FlatClassFeatures),methods:(0,r.Array)(t.FlatMethodFeatures)}),t.FlatFeaturesResults=(0,r.Record)({results:(0,r.Array)(t.FlatFeaturesResult),allFeatures:(0,r.Dictionary)(r.String)}),t.MutationLocation=(0,r.Record)({start:r.Number,end:r.Number,line:r.String,startLine:r.Number,endLine:r.Number}),t.AppliedMutation=(0,r.Record)({mutationType:r.String,location:t.MutationLocation,original:r.String,mutated:r.String,linesChanged:r.Number}),t.MutatedSource=(0,r.Record)({mutatedSource:r.String,mutatedSources:(0,r.Dictionary)(r.String),mutation:t.AppliedMutation}),t.MutationsResults=(0,r.Record)({source:(0,r.Dictionary)(r.String),mutatedSources:(0,r.Array)(t.MutatedSource)}),t.DisassembleResults=(0,r.Record)({disassemblies:(0,r.Dictionary)(r.String,r.String)}),t.ThrownException=(0,r.Record)({klass:r.String,stacktrace:r.String}).And((0,r.Partial)({message:r.String})),t.Console=(0,r.Union)((0,r.Literal)("STDOUT"),(0,r.Literal)("STDERR")),t.OutputLine=(0,r.Record)({console:t.Console,line:r.String,timestamp:r.String.withConstraint(e=>!isNaN(Date.parse(e)))}).And((0,r.Partial)({thread:r.Number})),t.PermissionRequest=(0,r.Record)({permission:t.Permission,granted:r.Boolean}),t.KILL_REASONS={massiveAllocation:"too large single allocation",exceededAllocationLimit:"exceeded total memory allocation limit",exceededLineLimit:"exceeded total line count limit"},t.SourceTaskResults=(0,r.Record)({klass:r.String,method:r.String,timeout:r.Boolean,outputLines:(0,r.Array)(t.OutputLine),permissionRequests:(0,r.Array)(t.PermissionRequest),interval:t.Interval,executionInterval:t.Interval,truncatedLines:r.Number}).And((0,r.Partial)({returned:r.String,threw:t.ThrownException,killReason:r.String})),t.ContainerExecutionResults=(0,r.Record)({klass:r.String,method:r.String,timeout:r.Boolean,outputLines:(0,r.Array)(t.OutputLine),interval:t.Interval,executionInterval:t.Interval,truncatedLines:r.Number}).And((0,r.Partial)({exitcode:r.Number})),t.CompletedTasks=(0,r.Partial)({snippet:t.Snippet,template:t.TemplatedSourceResult,compilation:t.CompiledSourceResult,kompilation:t.CompiledSourceResult,checkstyle:t.CheckstyleResults,ktlint:t.KtlintResults,complexity:t.FlatComplexityResults,features:t.FlatFeaturesResults,execution:t.SourceTaskResults,cexecution:t.ContainerExecutionResults,mutations:t.MutationsResults,disassemble:t.DisassembleResults}),t.TemplatingError=(0,r.Record)({name:r.String,line:r.Number,column:r.Number,message:r.String}),t.TemplatingFailed=(0,r.Record)({errors:(0,r.Array)(t.TemplatingError)}),t.SnippetTransformationError=(0,r.Record)({line:r.Number,column:r.Number,message:r.String}),t.SnippetTransformationFailed=(0,r.Record)({errors:(0,r.Array)(t.SnippetTransformationError)}),t.CompilationError=(0,r.Record)({message:r.String}).And((0,r.Partial)({location:t.SourceLocation})),t.CompilationFailed=(0,r.Record)({errors:(0,r.Array)(t.CompilationError)}),t.CheckstyleFailed=(0,r.Record)({errors:(0,r.Array)(t.CheckstyleError)}),t.KtlintFailed=(0,r.Record)({errors:(0,r.Array)(t.KtlintError)}),t.SourceError=(0,r.Record)({message:r.String}).And((0,r.Partial)({location:t.SourceLocation})),t.ComplexityFailed=(0,r.Record)({errors:(0,r.Array)(t.SourceError)}),t.FeaturesFailed=(0,r.Record)({errors:(0,r.Array)(t.SourceError)}),t.MutationsFailed=(0,r.Record)({errors:(0,r.Array)(t.SourceError)}),t.DisassembleFailed=(0,r.Record)({message:r.String}),t.ExecutionFailedResult=(0,r.Partial)({classNotFound:r.String,methodNotFound:r.String}),t.FailedTasks=(0,r.Partial)({template:t.TemplatingFailed,snippet:t.SnippetTransformationFailed,compilation:t.CompilationFailed,kompilation:t.CompilationFailed,checkstyle:t.CheckstyleFailed,ktlint:t.KtlintFailed,complexity:t.ComplexityFailed,execution:t.ExecutionFailedResult,cexecution:t.ExecutionFailedResult,features:t.FeaturesFailed,mutations:t.MutationsFailed,disassemble:t.DisassembleFailed}),t.Response=(0,r.Record)({request:t.Request,status:t.ServerStatus,completed:t.CompletedTasks,completedTasks:(0,r.Array)(t.Task),failed:t.FailedTasks,failedTasks:(0,r.Array)(t.Task),interval:t.Interval}).And((0,r.Partial)({email:r.String,audience:(0,r.Array)(r.String)}))}},function(e){e.O(0,[436,774,888,179],function(){return e(e.s=9536)}),_N_E=e.O()}]);