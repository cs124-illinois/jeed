import { Array, Boolean, Dictionary, Literal, Number, Partial, Record, Static, String, Union } from "runtypes"

export const Task = Union(
  Literal("template"),
  Literal("snippet"),
  Literal("compile"),
  Literal("kompile"),
  Literal("checkstyle"),
  Literal("ktlint"),
  Literal("complexity"),
  Literal("execute"),
  Literal("cexecute"),
  Literal("features"),
  Literal("mutations"),
  Literal("disassemble"),
)
export type Task = Static<typeof Task>

export const FlatSource = Record({
  path: String,
  contents: String,
})
export type FlatSource = Static<typeof FlatSource>

export const Permission = Record({
  klass: String,
  name: String,
}).And(
  Partial({
    actions: String,
  }),
)
export type Permission = Static<typeof Permission>

export const FileType = Union(Literal("JAVA"), Literal("KOTLIN"))
export type FileType = Static<typeof FileType>

export const Location = Record({ line: Number, column: Number })
export type Location = Static<typeof Location>

export const SourceRange = Record({
  start: Location,
  end: Location,
}).And(
  Partial({
    source: String,
  }),
)
export type SourceRange = Static<typeof SourceRange>

export const SourceLocation = Record({
  source: String,
  line: Number,
  column: Number,
})
export type SourceLocation = Static<typeof SourceLocation>

export const Interval = Record({
  start: String.withConstraint((s) => !isNaN(Date.parse(s))),
  end: String.withConstraint((s) => !isNaN(Date.parse(s))),
})
export type Interval = Static<typeof Interval>

export const intervalDuration = (interval: Interval) =>
  new Date(interval.end).valueOf() - new Date(interval.start).valueOf()

export const ServerStatus = Record({
  tasks: Array(Task),
  started: String.withConstraint((s) => !isNaN(Date.parse(s))),
  hostname: String,
  versions: Record({
    jeed: String,
    compiler: String,
    kompiler: String,
  }),
  counts: Record({
    submitted: Number,
    completed: Number,
    saved: Number,
  }),
  cache: Record({
    inUse: Boolean,
    sizeInMB: Number,
    hits: Number,
    misses: Number,
    hitRate: Number,
    evictionCount: Number,
    averageLoadPenalty: Number,
  }),
}).And(
  Partial({
    lastRequest: String.withConstraint((s) => !isNaN(Date.parse(s))),
  }),
)
export type ServerStatus = Static<typeof ServerStatus>

export const SnippetArguments = Partial({
  indent: Number,
  fileType: FileType,
  noEmptyMain: Boolean,
})
export type SnippetArguments = Static<typeof SnippetArguments>

export const CompilationArguments = Partial({
  wError: Boolean,
  XLint: String,
  enablePreview: Boolean,
  useCache: Boolean,
  waitForCache: Boolean,
  parameters: Boolean,
  debugInfo: Boolean,
})
export type CompilationArguments = Static<typeof CompilationArguments>

export const KompilationArguments = Partial({
  verbose: Boolean,
  allWarningsAsErrors: Boolean,
  useCache: Boolean,
  waitForCache: Boolean,
})
export type KompilationArguments = Static<typeof KompilationArguments>

export const CheckstyleArguments = Partial({
  sources: Array(String),
  failOnError: Boolean,
})
export type CheckstyleArguments = Static<typeof CheckstyleArguments>

export const KtLintArguments = Partial({
  sources: Array(String),
  failOnError: Boolean,
  indent: Number,
  maxLineLength: Number,
  script: Boolean,
})
export type KtLintArguments = Static<typeof KtLintArguments>

export const ClassLoaderConfiguration = Partial({
  whitelistedClasses: Array(String),
  blacklistedClasses: Array(String),
  unsafeExceptions: Array(String),
  isolatedClasses: Array(String),
})
export type ClassLoaderConfiguration = Static<typeof ClassLoaderConfiguration>

export const SourceExecutionArguments = Partial({
  klass: String,
  method: String,
  timeout: Number,
  permissions: Array(Permission),
  maxExtraThreads: Number,
  maxOutputLines: Number,
  maxIOBytes: Number,
  classLoaderConfiguration: ClassLoaderConfiguration,
  dryRun: Boolean,
  waitForShutdown: Boolean,
  returnTimeout: Number,
  permissionBlackList: Boolean,
  cpuTimeout: Number,
  pollInterval: Number,
})
export type SourceExecutionArguments = Static<typeof SourceExecutionArguments>

export const ContainerExecutionArguments = Partial({
  klass: String,
  method: String,
  image: String,
  timeout: Number,
  maxOutputLines: Number,
  containerArguments: String,
})
export type ContainerExecutionArguments = Static<typeof ContainerExecutionArguments>

export const MutationsArguments = Partial({
  limit: Number,
  suppressWithComments: Boolean,
})
export type MutationsArguments = Static<typeof MutationsArguments>

export const TaskArguments = Partial({
  snippet: SnippetArguments,
  compilation: CompilationArguments,
  kompilation: KompilationArguments,
  checkstyle: CheckstyleArguments,
  ktlint: KtLintArguments,
  execution: SourceExecutionArguments,
  cexecution: ContainerExecutionArguments,
  mutations: MutationsArguments,
})
export type TaskArguments = Static<typeof TaskArguments>

export const Request = Record({
  tasks: Array(Task),
  label: String,
}).And(
  Partial({
    sources: Array(FlatSource),
    templates: Array(FlatSource),
    snippet: String,
    arguments: TaskArguments,
    checkForSnippet: Boolean,
  }),
)
export type Request = Static<typeof Request>

export const Snippet = Record({
  sources: Dictionary(String),
  originalSource: String,
  rewrittenSource: String,
  snippetRange: SourceRange,
  wrappedClassName: String,
  looseCodeMethodName: String,
  fileType: FileType,
})
export type Snippet = Static<typeof Snippet>

export const TemplatedSourceResult = Record({
  sources: Dictionary(String),
  originalSources: Dictionary(String),
})
export type TemplatedSourceResult = Static<typeof TemplatedSourceResult>

export const CompilationMessage = Record({
  kind: String,
  message: String,
}).And(
  Partial({
    location: SourceLocation,
  }),
)
export type CompilationMessage = Static<typeof CompilationMessage>

export const CompiledSourceResult = Record({
  messages: Array(CompilationMessage),
  compiled: String.withConstraint((s) => !isNaN(Date.parse(s))),
  interval: Interval,
  compilerName: String,
  cached: Boolean,
})
export type CompiledSourceResult = Static<typeof CompiledSourceResult>

export const CheckstyleError = Record({
  severity: String,
  location: SourceLocation,
  message: String,
}).And(
  Partial({
    sourceName: String,
  }),
)
export type CheckstyleError = Static<typeof CheckstyleError>

export const CheckstyleResults = Record({
  errors: Array(CheckstyleError),
})
export type CheckstyleResults = Static<typeof CheckstyleResults>

export const KtlintError = Record({
  ruleId: String,
  detail: String,
  location: SourceLocation,
})
export type KtlintError = Static<typeof KtlintError>

export const KtlintResults = Record({
  errors: Array(KtlintError),
})
export type KtlintResults = Static<typeof KtlintError>

export const FlatClassComplexity = Record({
  name: String,
  path: String,
  range: SourceRange,
  complexity: Number,
})
export type FlatClassComplexity = Static<typeof FlatClassComplexity>
export const FlatMethodComplexity = FlatClassComplexity
export type FlatMethodComplexity = FlatClassComplexity

export const FlatComplexityResult = Record({
  source: String,
  classes: Array(FlatClassComplexity),
  methods: Array(FlatMethodComplexity),
})
export type FlatComplexityResult = Static<typeof FlatComplexityResult>

export const FlatComplexityResults = Record({
  results: Array(FlatComplexityResult),
})
export type FlatComplexityResults = Static<typeof FlatComplexityResults>

export const Feature = Union(
  Literal("EMPTY"),
  Literal("LOCAL_VARIABLE_DECLARATIONS"),
  Literal("VARIABLE_ASSIGNMENTS"),
  Literal("VARIABLE_REASSIGNMENTS"),
  Literal("FINAL_VARIABLE"),
  //
  Literal("UNARY_OPERATORS"),
  Literal("ARITHMETIC_OPERATORS"),
  Literal("BITWISE_OPERATORS"),
  Literal("ASSIGNMENT_OPERATORS"),
  Literal("TERNARY_OPERATOR"),
  Literal("COMPARISON_OPERATORS"),
  Literal("LOGICAL_OPERATORS"),
  Literal("PRIMITIVE_CASTING"),
  //
  Literal("IF_STATEMENTS"),
  Literal("ELSE_STATEMENTS"),
  Literal("ELSE_IF"),
  //
  Literal("ARRAYS"),
  Literal("ARRAY_ACCESS"),
  Literal("ARRAY_LITERAL"),
  Literal("MULTIDIMENSIONAL_ARRAYS"),
  //
  Literal("FOR_LOOPS"),
  Literal("ENHANCED_FOR"),
  Literal("WHILE_LOOPS"),
  Literal("DO_WHILE_LOOPS"),
  Literal("BREAK"),
  Literal("CONTINUE"),
  //
  Literal("NESTED_IF"),
  Literal("NESTED_FOR"),
  Literal("NESTED_WHILE"),
  Literal("NESTED_DO_WHILE"),
  Literal("NESTED_CLASS"),
  Literal("NESTED_LOOP"),
  //
  Literal("METHOD"),
  Literal("RETURN"),
  Literal("CONSTRUCTOR"),
  Literal("GETTER"),
  Literal("SETTER"),
  Literal("METHOD_CALL"),
  //
  Literal("STRING"),
  Literal("NULL"),
  //
  Literal("CASTING"),
  Literal("TYPE_INFERENCE"),
  Literal("INSTANCEOF"),
  //
  Literal("CLASS"),
  Literal("IMPLEMENTS"),
  Literal("INTERFACE"),
  //
  Literal("EXTENDS"),
  Literal("SUPER"),
  Literal("OVERRIDE"),
  //
  Literal("TRY_BLOCK"),
  Literal("FINALLY"),
  Literal("ASSERT"),
  Literal("THROW"),
  Literal("THROWS"),
  //
  Literal("NEW_KEYWORD"),
  Literal("THIS"),
  Literal("REFERENCE_EQUALITY"),
  Literal("CLASS_FIELD"),
  Literal("EQUALITY"),
  //
  Literal("VISIBILITY_MODIFIERS"),
  Literal("STATIC_METHOD"),
  Literal("FINAL_METHOD"),
  Literal("ABSTRACT_METHOD"),
  Literal("STATIC_FIELD"),
  Literal("FINAL_FIELD"),
  Literal("FINAL_CLASS"),
  Literal("ABSTRACT_CLASS"),
  //
  Literal("IMPORT"),
  //
  Literal("ANONYMOUS_CLASSES"),
  Literal("LAMBDA_EXPRESSIONS"),
  Literal("GENERIC_CLASS"),
  Literal("SWITCH"),
  Literal("SWITCH_EXPRESSION"),
  Literal("STREAM"),
  Literal("ENUM"),
  //
  Literal("COMPARABLE"),
  Literal("RECORD"),
  Literal("BOXING_CLASSES"),
  Literal("TYPE_PARAMETERS"),
  Literal("PRINT_STATEMENTS"),
  //
  Literal("DOT_NOTATION"),
  Literal("DOTTED_METHOD_CALL"),
  Literal("DOTTED_VARIABLE_ACCESS"),
  //
  Literal("BLOCK_START"),
  Literal("BLOCK_END"),
  Literal("STATEMENT_START"),
  Literal("STATEMENT_END"),
  //
  Literal("NESTED_METHOD"),
  Literal("JAVA_PRINT_STATEMENTS"),
  Literal("REQUIRE_OR_CHECK"),
  Literal("FOR_LOOP_STEP"),
  Literal("ELVIS_OPERATOR"),
  Literal("FOR_LOOP_RANGE"),
  Literal("SECONDARY_CONSTRUCTOR"),
  Literal("JAVA_EQUALITY"),
  Literal("COMPANION_OBJECT"),
  Literal("HAS_COMPANION_OBJECT"),
  Literal("NULLABLE_TYPE"),
  Literal("WHEN_STATEMENT"),
  Literal("EXPLICIT_TYPE"),
  Literal("DATA_CLASS"),
  Literal("OPEN_CLASS"),
  Literal("OPEN_METHOD"),
  Literal("COLLECTION_INDEXING"),
  Literal("MULTILEVEL_COLLECTION_INDEXING"),
  Literal("SINGLETON"),
  Literal("FUNCTIONAL_INTERFACE"),
  Literal("ANONYMOUS_FUNCTION"),
  Literal("ABSTRACT_FIELD"),
  Literal("IF_EXPRESSIONS"),
  Literal("TRY_EXPRESSIONS"),
  Literal("WHEN_EXPRESSIONS"),
  Literal("SAFE_CALL_OPERATOR"),
  Literal("UNSAFE_CALL_OPERATOR"),
  Literal("WHEN_ENTRY"),
  Literal("LAST_WHEN_ENTRY"),
)
export type Feature = Static<typeof Feature>

export const JAVA_ONLY_FEATURES = new Set(
  Array(Feature).check([
    "TERNARY_OPERATOR",
    "ARRAY_ACCESS",
    "MULTIDIMENSIONAL_ARRAYS",
    "ENHANCED_FOR",
    "THROWS",
    "NEW_KEYWORD",
    "FINAL_METHOD",
    "FINAL_CLASS",
    "SWITCH",
    "SWITCH_EXPRESSION",
    "STREAM",
    "RECORD",
    "BOXING_CLASSES",
  ]),
)

export const KOTLIN_ONLY_FEATURES = new Set(
  Array(Feature).check([
    "NESTED_METHOD",
    "JAVA_PRINT_STATEMENTS",
    "REQUIRE_OR_CHECK",
    "FOR_LOOP_STEP",
    "ELVIS_OPERATOR",
    "FOR_LOOP_RANGE",
    "SECONDARY_CONSTRUCTOR",
    "JAVA_EQUALITY",
    "COMPANION_OBJECT",
    "HAS_COMPANION_OBJECT",
    "NULLABLE_TYPE",
    "WHEN_STATEMENT",
    "EXPLICIT_TYPE",
    "DATA_CLASS",
    "OPEN_CLASS",
    "OPEN_METHOD",
    "COLLECTION_INDEXING",
    "MULTILEVEL_COLLECTION_INDEXING",
    "SINGLETON",
    "FUNCTIONAL_INTERFACE",
    "ANONYMOUS_FUNCTION",
    "ABSTRACT_FIELD",
    "IF_EXPRESSIONS",
    "TRY_EXPRESSIONS",
    "WHEN_EXPRESSIONS",
    "SAFE_CALL_OPERATOR",
    "UNSAFE_CALL_OPERATOR",
    "WHEN_ENTRY",
    "LAST_WHEN_ENTRY",
  ]),
)

export const STRUCTURAL_FEATURES = new Set(["BLOCK_START", "BLOCK_END", "STATEMENT_START", "STATEMENT_END"])

export const ALL_FEATURES = new Set(Feature.alternatives.map((literal) => literal.value))

export const JAVA_FEATURES = new Set(
  [...ALL_FEATURES].filter((f) => f !== "EMPTY" && !KOTLIN_ONLY_FEATURES.has(f) && !STRUCTURAL_FEATURES.has(f)),
)

export const KOTLIN_FEATURES = new Set(
  [...ALL_FEATURES].filter((f) => f !== "EMPTY" && !JAVA_ONLY_FEATURES.has(f) && !STRUCTURAL_FEATURES.has(f)),
)

export const ORDERED_FEATURES = Array(Feature).check([
  "LOCAL_VARIABLE_DECLARATIONS",
  "VARIABLE_ASSIGNMENTS",
  "VARIABLE_REASSIGNMENTS",
  "FINAL_VARIABLE",
  "UNARY_OPERATORS",
  "ARITHMETIC_OPERATORS",
  "BITWISE_OPERATORS",
  "ASSIGNMENT_OPERATORS",
  "TERNARY_OPERATOR",
  "COMPARISON_OPERATORS",
  "LOGICAL_OPERATORS",
  "PRIMITIVE_CASTING",
  "IF_STATEMENTS",
  "ELSE_STATEMENTS",
  "ELSE_IF",
  "ARRAYS",
  "ARRAY_ACCESS",
  "ARRAY_LITERAL",
  "MULTIDIMENSIONAL_ARRAYS",
  "FOR_LOOPS",
  "ENHANCED_FOR",
  "WHILE_LOOPS",
  "DO_WHILE_LOOPS",
  "BREAK",
  "CONTINUE",
  "NESTED_IF",
  "NESTED_FOR",
  "NESTED_WHILE",
  "NESTED_DO_WHILE",
  "NESTED_CLASS",
  "NESTED_LOOP",
  "METHOD",
  "RETURN",
  "CONSTRUCTOR",
  "GETTER",
  "SETTER",
  "STRING",
  "NULL",
  "CASTING",
  "TYPE_INFERENCE",
  "INSTANCEOF",
  "CLASS",
  "IMPLEMENTS",
  "INTERFACE",
  "EXTENDS",
  "SUPER",
  "OVERRIDE",
  "TRY_BLOCK",
  "FINALLY",
  "ASSERT",
  "THROW",
  "THROWS",
  "NEW_KEYWORD",
  "THIS",
  "REFERENCE_EQUALITY",
  "CLASS_FIELD",
  "EQUALITY",
  "VISIBILITY_MODIFIERS",
  "STATIC_METHOD",
  "FINAL_METHOD",
  "ABSTRACT_METHOD",
  "STATIC_FIELD",
  "FINAL_FIELD",
  "ABSTRACT_FIELD",
  "FINAL_CLASS",
  "ABSTRACT_CLASS",
  "IMPORT",
  "ANONYMOUS_CLASSES",
  "LAMBDA_EXPRESSIONS",
  "GENERIC_CLASS",
  "SWITCH",
  "STREAM",
  "ENUM",
  "COMPARABLE",
  "RECORD",
  "BOXING_CLASSES",
  "TYPE_PARAMETERS",
  "PRINT_STATEMENTS",
  "DOT_NOTATION",
  "DOTTED_METHOD_CALL",
  "DOTTED_VARIABLE_ACCESS",
  "BLOCK_START",
  "BLOCK_END",
  "STATEMENT_START",
  "STATEMENT_END",
  "NESTED_METHOD",
  "JAVA_PRINT_STATEMENTS",
  "REQUIRE_OR_CHECK",
  "FOR_LOOP_STEP",
  "ELVIS_OPERATOR",
  "FOR_LOOP_RANGE",
  "SECONDARY_CONSTRUCTOR",
  "JAVA_EQUALITY",
  "COMPANION_OBJECT",
  "HAS_COMPANION_OBJECT",
  "NULLABLE_TYPE",
  "WHEN_STATEMENT",
  "EXPLICIT_TYPE",
  "DATA_CLASS",
  "OPEN_CLASS",
  "OPEN_METHOD",
  "COLLECTION_INDEXING",
  "MULTILEVEL_COLLECTION_INDEXING",
  "SINGLETON",
  "FUNCTIONAL_INTERFACE",
  "ANONYMOUS_FUNCTION",
  "IF_EXPRESSIONS",
  "TRY_EXPRESSIONS",
  "WHEN_EXPRESSIONS",
  "WHEN_ENTRY",
  "LAST_WHEN_ENTRY",
  "SWITCH_EXPRESSION",
  "METHOD_CALL",
])

export const LocatedFeature = Record({
  feature: Feature,
  location: Location,
})
export type LocatedFeature = Static<typeof LocatedFeature>

export const FeatureValue = Record({
  featureMap: Dictionary(Number, Feature),
  featureList: Array(LocatedFeature),
  importList: Array(String),
  typeList: Array(String),
  identifierList: Array(String),
  dottedMethodList: Array(String),
}).And(
  Partial({
    methodList: Array(String),
  }),
)
export type FeatureValue = Static<typeof FeatureValue>

export const FlatClassFeatures = Record({
  name: String,
  path: String,
  features: FeatureValue,
}).And(
  Partial({
    range: SourceRange,
  }),
)
export type FlatClassFeatures = Static<typeof FlatClassFeatures>

export const FlatMethodFeatures = FlatClassFeatures
export type FlatMethodFeatures = FlatClassFeatures

export const FlatUnitFeatures = Record({
  name: String,
  path: String,
  range: SourceRange,
  features: FeatureValue,
})
export type FlatUnitFeatures = Static<typeof FlatUnitFeatures>

export const FlatFeaturesResult = Record({
  source: String,
  unit: FlatUnitFeatures,
  classes: Array(FlatClassFeatures),
  methods: Array(FlatMethodFeatures),
})
export type FlatFeaturesResult = Static<typeof FlatFeaturesResult>

export const FlatFeaturesResults = Record({
  results: Array(FlatFeaturesResult),
  allFeatures: Dictionary(String),
})
export type FlatFeaturesResults = Static<typeof FlatFeaturesResults>

export const MutationLocation = Record({
  start: Number,
  end: Number,
  line: String,
  startLine: Number,
  endLine: Number,
})
export type MutationLocation = Static<typeof MutationLocation>

export const AppliedMutation = Record({
  mutationType: String,
  location: MutationLocation,
  original: String,
  mutated: String,
  linesChanged: Number,
})
export type AppliedMutation = Static<typeof AppliedMutation>

export const MutatedSource = Record({
  mutatedSource: String,
  mutatedSources: Dictionary(String),
  mutation: AppliedMutation,
})
export type MutatedSource = Static<typeof MutatedSource>

export const MutationsResults = Record({
  source: Dictionary(String),
  mutatedSources: Array(MutatedSource),
})
export type MutationsResults = Static<typeof MutationsResults>

export const DisassembleResults = Record({
  disassemblies: Dictionary(String, String),
})
export type DisassembleResults = Static<typeof DisassembleResults>

export const ThrownException = Record({
  klass: String,
  stacktrace: String,
}).And(
  Partial({
    message: String,
  }),
)
export type ThrownException = Static<typeof ThrownException>

export const Console = Union(Literal("STDOUT"), Literal("STDERR"))
export type Console = Static<typeof Console>

export const OutputLine = Record({
  console: Console,
  line: String,
  timestamp: String.withConstraint((s) => !isNaN(Date.parse(s))),
}).And(
  Partial({
    thread: Number,
  }),
)
export type OutputLine = Static<typeof OutputLine>

export const PermissionRequest = Record({
  permission: Permission,
  granted: Boolean,
})
export type PermissionRequest = Static<typeof PermissionRequest>

export const KILL_REASONS: { [key: string]: string } = {
  massiveAllocation: "too large single allocation",
  exceededAllocationLimit: "exceeded total memory allocation limit",
  exceededLineLimit: "exceeded total line count limit",
}

export const SourceTaskResults = Record({
  klass: String,
  method: String,
  timeout: Boolean,
  outputLines: Array(OutputLine),
  permissionRequests: Array(PermissionRequest),
  interval: Interval,
  executionInterval: Interval,
  truncatedLines: Number,
}).And(
  Partial({
    returned: String,
    threw: ThrownException,
    killReason: String,
  }),
)
export type SourceTaskResults = Static<typeof SourceTaskResults>

export const ContainerExecutionResults = Record({
  klass: String,
  method: String,
  timeout: Boolean,
  outputLines: Array(OutputLine),
  interval: Interval,
  executionInterval: Interval,
  truncatedLines: Number,
}).And(
  Partial({
    exitcode: Number,
  }),
)
export type ContainerExecutionResults = Static<typeof ContainerExecutionResults>

export const CompletedTasks = Partial({
  snippet: Snippet,
  template: TemplatedSourceResult,
  compilation: CompiledSourceResult,
  kompilation: CompiledSourceResult,
  checkstyle: CheckstyleResults,
  ktlint: KtlintResults,
  complexity: FlatComplexityResults,
  features: FlatFeaturesResults,
  execution: SourceTaskResults,
  cexecution: ContainerExecutionResults,
  mutations: MutationsResults,
  disassemble: DisassembleResults,
})
export type CompletedTasks = Static<typeof CompletedTasks>

export const TemplatingError = Record({
  name: String,
  line: Number,
  column: Number,
  message: String,
})
export type TemplatingError = Static<typeof TemplatingError>

export const TemplatingFailed = Record({
  errors: Array(TemplatingError),
})
export type TemplatingFailed = Static<typeof TemplatingFailed>

export const SnippetTransformationError = Record({
  line: Number,
  column: Number,
  message: String,
})
export type SnippetTransformationError = Static<typeof SnippetTransformationError>

export const SnippetTransformationFailed = Record({
  errors: Array(SnippetTransformationError),
})
export type SnippetTransformationFailed = Static<typeof SnippetTransformationFailed>

export const CompilationError = Record({
  message: String,
}).And(
  Partial({
    location: SourceLocation,
  }),
)
export type CompilationError = Static<typeof CompilationError>

export const CompilationFailed = Record({
  errors: Array(CompilationError),
})
export type CompilationFailed = Static<typeof CompilationFailed>

export const CheckstyleFailed = Record({
  errors: Array(CheckstyleError),
})
export type CheckstyleFailed = Static<typeof CheckstyleFailed>

export const KtlintFailed = Record({
  errors: Array(KtlintError),
})
export type KtlintFailed = Static<typeof KtlintFailed>

export const SourceError = Record({
  message: String,
}).And(
  Partial({
    location: SourceLocation,
  }),
)
export type SourceError = Static<typeof SourceError>

export const ComplexityFailed = Record({
  errors: Array(SourceError),
})
export type ComplexityFailed = Static<typeof ComplexityFailed>

export const FeaturesFailed = Record({
  errors: Array(SourceError),
})
export type FeaturesFailed = Static<typeof FeaturesFailed>

export const MutationsFailed = Record({
  errors: Array(SourceError),
})
export type MutationsFailed = Static<typeof MutationsFailed>

export const DisassembleFailed = Record({
  message: String,
})
export type DisassembleFailed = Static<typeof DisassembleFailed>

export const ExecutionFailedResult = Partial({
  classNotFound: String,
  methodNotFound: String,
})
export type ExecutionFailedResult = Static<typeof ExecutionFailedResult>

export const FailedTasks = Partial({
  template: TemplatingFailed,
  snippet: SnippetTransformationFailed,
  compilation: CompilationFailed,
  kompilation: CompilationFailed,
  checkstyle: CheckstyleFailed,
  ktlint: KtlintFailed,
  complexity: ComplexityFailed,
  execution: ExecutionFailedResult,
  cexecution: ExecutionFailedResult,
  features: FeaturesFailed,
  mutations: MutationsFailed,
  disassemble: DisassembleFailed,
})
export type FailedTasks = Static<typeof FailedTasks>

export const Response = Record({
  request: Request,
  status: ServerStatus,
  completed: CompletedTasks,
  completedTasks: Array(Task),
  failed: FailedTasks,
  failedTasks: Array(Task),
  interval: Interval,
}).And(
  Partial({
    email: String,
    audience: Array(String),
  }),
)
export type Response = Static<typeof Response>
