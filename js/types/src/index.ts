import { Array, Boolean, Literal, Number, Object, Record, Static, String, Union } from "runtypes"

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

export const FlatSource = Object({
  path: String,
  contents: String,
})
export type FlatSource = Static<typeof FlatSource>

export const Permission = Object({
  klass: String,
  name: String,
  actions: String.optional(),
})
export type Permission = Static<typeof Permission>

export const FileType = Union(Literal("JAVA"), Literal("KOTLIN"))
export type FileType = Static<typeof FileType>

export const Location = Object({ line: Number, column: Number })
export type Location = Static<typeof Location>

export const SourceRange = Object({
  start: Location,
  end: Location,
  source: String.optional(),
})
export type SourceRange = Static<typeof SourceRange>

export const SourceLocation = Object({
  source: String,
  line: Number,
  column: Number,
})
export type SourceLocation = Static<typeof SourceLocation>

export const Interval = Object({
  start: String.withConstraint((s) => !isNaN(Date.parse(s))),
  end: String.withConstraint((s) => !isNaN(Date.parse(s))),
})
export type Interval = Static<typeof Interval>

export const intervalDuration = (interval: Interval) =>
  new Date(interval.end).valueOf() - new Date(interval.start).valueOf()

export const ServerStatus = Object({
  tasks: Array(Task),
  started: String.withConstraint((s) => !isNaN(Date.parse(s))),
  hostname: String,
  versions: Object({
    jeed: String,
    compiler: String,
    kompiler: String,
  }),
  counts: Object({
    submitted: Number,
    completed: Number,
    saved: Number,
  }),
  cache: Object({
    inUse: Boolean,
    sizeInMB: Number,
    hits: Number,
    misses: Number,
    hitRate: Number,
    evictionCount: Number,
    averageLoadPenalty: Number,
  }),
  lastRequest: String.withConstraint((s) => !isNaN(Date.parse(s))).optional(),
})
export type ServerStatus = Static<typeof ServerStatus>

export const SnippetArguments = Object({
  indent: Number.optional(),
  fileType: FileType.optional(),
  noEmptyMain: Boolean.optional(),
})
export type SnippetArguments = Static<typeof SnippetArguments>

export const CompilationArguments = Object({
  wError: Boolean.optional(),
  XLint: String.optional(),
  enablePreview: Boolean.optional(),
  useCache: Boolean.optional(),
  waitForCache: Boolean.optional(),
  parameters: Boolean.optional(),
  debugInfo: Boolean.optional(),
  isolatedClassLoader: Boolean.optional(),
})
export type CompilationArguments = Static<typeof CompilationArguments>

export const KompilationArguments = Object({
  verbose: Boolean.optional(),
  allWarningsAsErrors: Boolean.optional(),
  useCache: Boolean.optional(),
  waitForCache: Boolean.optional(),
  parameters: Boolean.optional(),
  jvmTarget: String.optional(),
  isolatedClassLoader: Boolean.optional(),
})
export type KompilationArguments = Static<typeof KompilationArguments>

export const CheckstyleArguments = Object({
  sources: Array(String).optional(),
  failOnError: Boolean.optional(),
  skipUnmapped: Boolean.optional(),
  suppressions: Array(String).optional(),
})
export type CheckstyleArguments = Static<typeof CheckstyleArguments>

export const KtLintArguments = Object({
  sources: Array(String).optional(),
  failOnError: Boolean.optional(),
  indent: Number.optional(),
  maxLineLength: Number.optional(),
  script: Boolean.optional(),
})
export type KtLintArguments = Static<typeof KtLintArguments>

export const ClassLoaderConfiguration = Object({
  whitelistedClasses: Array(String).optional(),
  blacklistedClasses: Array(String).optional(),
  unsafeExceptions: Array(String).optional(),
  isolatedClasses: Array(String).optional(),
})
export type ClassLoaderConfiguration = Static<typeof ClassLoaderConfiguration>

export const SourceExecutionArguments = Object({
  klass: String.optional(),
  method: String.optional(),
  timeout: Number.optional(),
  permissions: Array(Permission).optional(),
  maxExtraThreads: Number.optional(),
  maxOutputLines: Number.optional(),
  maxIOBytes: Number.optional(),
  classLoaderConfiguration: ClassLoaderConfiguration.optional(),
  dryRun: Boolean.optional(),
  waitForShutdown: Boolean.optional(),
  returnTimeout: Number.optional(),
  permissionBlackList: Boolean.optional(),
  cpuTimeout: Number.optional(),
  pollInterval: Number.optional(),
})
export type SourceExecutionArguments = Static<typeof SourceExecutionArguments>

export const ContainerExecutionArguments = Object({
  klass: String.optional(),
  method: String.optional(),
  image: String.optional(),
  timeout: Number.optional(),
  maxOutputLines: Number.optional(),
  containerArguments: String.optional(),
})
export type ContainerExecutionArguments = Static<typeof ContainerExecutionArguments>

export const MutationsArguments = Object({
  limit: Number.optional(),
  suppressWithComments: Boolean.optional(),
})
export type MutationsArguments = Static<typeof MutationsArguments>

export const TaskArguments = Object({
  snippet: SnippetArguments.optional(),
  compilation: CompilationArguments.optional(),
  kompilation: KompilationArguments.optional(),
  checkstyle: CheckstyleArguments.optional(),
  ktlint: KtLintArguments.optional(),
  execution: SourceExecutionArguments.optional(),
  cexecution: ContainerExecutionArguments.optional(),
  mutations: MutationsArguments.optional(),
})
export type TaskArguments = Static<typeof TaskArguments>

export const Request = Object({
  tasks: Array(Task),
  label: String.optional(),
  sources: Array(FlatSource).optional(),
  templates: Array(FlatSource).optional(),
  snippet: String.optional(),
  arguments: TaskArguments.optional(),
  checkForSnippet: Boolean.optional(),
})
export type Request = Static<typeof Request>

export const SnippetProperties = Object({
  importCount: Number,
  looseCount: Number,
  methodCount: Number,
  classCount: Number,
})
export type SnippetProperties = Static<typeof SnippetProperties>

export const Snippet = Object({
  sources: Record(String, String),
  originalSource: String,
  rewrittenSource: String,
  snippetRange: SourceRange,
  wrappedClassName: String,
  looseCodeMethodName: String,
  fileType: FileType,
  snippetProperties: SnippetProperties.optional(),
})
export type Snippet = Static<typeof Snippet>

export const TemplatedSourceResult = Object({
  sources: Record(String, String),
  originalSources: Record(String, String),
})
export type TemplatedSourceResult = Static<typeof TemplatedSourceResult>

export const CompilationMessage = Object({
  kind: String,
  message: String,
  location: SourceLocation.optional(),
})
export type CompilationMessage = Static<typeof CompilationMessage>

export const CompiledSourceResult = Object({
  messages: Array(CompilationMessage),
  compiled: String.withConstraint((s) => !isNaN(Date.parse(s))),
  interval: Interval,
  compilerName: String,
  cached: Boolean,
})
export type CompiledSourceResult = Static<typeof CompiledSourceResult>

export const CheckstyleError = Object({
  severity: String,
  location: SourceLocation,
  message: String,
  sourceName: String.optional(),
})
export type CheckstyleError = Static<typeof CheckstyleError>

export const CheckstyleResults = Object({
  errors: Array(CheckstyleError),
})
export type CheckstyleResults = Static<typeof CheckstyleResults>

export const KtlintError = Object({
  ruleId: String,
  detail: String,
  location: SourceLocation,
})
export type KtlintError = Static<typeof KtlintError>

export const KtlintResults = Object({
  errors: Array(KtlintError),
})
export type KtlintResults = Static<typeof KtlintError>

export const FlatClassComplexity = Object({
  name: String,
  path: String,
  range: SourceRange,
  complexity: Number,
})
export type FlatClassComplexity = Static<typeof FlatClassComplexity>
export const FlatMethodComplexity = FlatClassComplexity
export type FlatMethodComplexity = FlatClassComplexity

export const FlatComplexityResult = Object({
  source: String,
  classes: Array(FlatClassComplexity),
  methods: Array(FlatMethodComplexity),
})
export type FlatComplexityResult = Static<typeof FlatComplexityResult>

export const FlatComplexityResults = Object({
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

export const LocatedFeature = Object({
  feature: Feature,
  location: Location,
})
export type LocatedFeature = Static<typeof LocatedFeature>

export const FeatureValue = Object({
  featureMap: Record(String, Number),
  featureList: Array(LocatedFeature),
  importList: Array(String),
  typeList: Array(String),
  identifierList: Array(String),
  dottedMethodList: Array(String),
  methodList: Array(String).optional(),
})
export type FeatureValue = Static<typeof FeatureValue>

export const FlatClassFeatures = Object({
  name: String,
  path: String,
  features: FeatureValue,
  range: SourceRange.optional(),
})
export type FlatClassFeatures = Static<typeof FlatClassFeatures>

export const FlatMethodFeatures = FlatClassFeatures
export type FlatMethodFeatures = FlatClassFeatures

export const FlatUnitFeatures = Object({
  name: String,
  path: String,
  range: SourceRange,
  features: FeatureValue,
})
export type FlatUnitFeatures = Static<typeof FlatUnitFeatures>

export const FlatFeaturesResult = Object({
  source: String,
  unit: FlatUnitFeatures,
  classes: Array(FlatClassFeatures).optional(),
  methods: Array(FlatMethodFeatures).optional(),
})
export type FlatFeaturesResult = Static<typeof FlatFeaturesResult>

export const FlatFeaturesResults = Object({
  results: Array(FlatFeaturesResult),
  allFeatures: Record(String, String),
})
export type FlatFeaturesResults = Static<typeof FlatFeaturesResults>

export const MutationLocation = Object({
  start: Number,
  end: Number,
  line: String,
  startLine: Number,
  endLine: Number,
})
export type MutationLocation = Static<typeof MutationLocation>

export const AppliedMutation = Object({
  mutationType: String,
  location: MutationLocation,
  original: String,
  mutated: String,
  linesChanged: Number,
  mightNotCompile: Boolean.optional(),
})
export type AppliedMutation = Static<typeof AppliedMutation>

export const MutatedSource = Object({
  mutatedSource: String,
  mutatedSources: Record(String, String),
  mutation: AppliedMutation,
})
export type MutatedSource = Static<typeof MutatedSource>

export const MutationsResults = Object({
  source: Record(String, String),
  mutatedSources: Array(MutatedSource),
})
export type MutationsResults = Static<typeof MutationsResults>

export const DisassembleResults = Object({
  disassemblies: Record(String, String),
})
export type DisassembleResults = Static<typeof DisassembleResults>

export const ThrownException = Object({
  klass: String,
  stacktrace: String,
  message: String.optional(),
})
export type ThrownException = Static<typeof ThrownException>

export const Console = Union(Literal("STDOUT"), Literal("STDERR"))
export type Console = Static<typeof Console>

export const OutputLine = Object({
  console: Console,
  line: String,
  timestamp: String.withConstraint((s) => !isNaN(Date.parse(s))),
  thread: Number.optional(),
})
export type OutputLine = Static<typeof OutputLine>

export const PermissionRequest = Object({
  permission: Permission,
  granted: Boolean,
})
export type PermissionRequest = Static<typeof PermissionRequest>

export const KILL_REASONS: { [key: string]: string } = {
  massiveAllocation: "too large single allocation",
  exceededAllocationLimit: "exceeded total memory allocation limit",
  exceededLineLimit: "exceeded total line count limit",
}

export const SourceTaskResults = Object({
  klass: String,
  method: String,
  timeout: Boolean,
  outputLines: Array(OutputLine),
  permissionRequests: Array(PermissionRequest),
  interval: Interval,
  executionInterval: Interval,
  truncatedLines: Number,
  returned: String.optional(),
  threw: ThrownException.optional(),
  killReason: String.optional(),
})
export type SourceTaskResults = Static<typeof SourceTaskResults>

export const ContainerExecutionResults = Object({
  klass: String,
  method: String,
  timeout: Boolean,
  outputLines: Array(OutputLine),
  interval: Interval,
  executionInterval: Interval,
  truncatedLines: Number,
  exitcode: Number.optional(),
})
export type ContainerExecutionResults = Static<typeof ContainerExecutionResults>

export const CompletedTasks = Object({
  snippet: Snippet.optional(),
  template: TemplatedSourceResult.optional(),
  compilation: CompiledSourceResult.optional(),
  kompilation: CompiledSourceResult.optional(),
  checkstyle: CheckstyleResults.optional(),
  ktlint: KtlintResults.optional(),
  complexity: FlatComplexityResults.optional(),
  features: FlatFeaturesResults.optional(),
  execution: SourceTaskResults.optional(),
  cexecution: ContainerExecutionResults.optional(),
  mutations: MutationsResults.optional(),
  disassemble: DisassembleResults.optional(),
})
export type CompletedTasks = Static<typeof CompletedTasks>

export const TemplatingError = Object({
  name: String,
  line: Number,
  column: Number,
  message: String,
})
export type TemplatingError = Static<typeof TemplatingError>

export const TemplatingFailed = Object({
  errors: Array(TemplatingError),
})
export type TemplatingFailed = Static<typeof TemplatingFailed>

export const SnippetTransformationError = Object({
  line: Number,
  column: Number,
  message: String,
})
export type SnippetTransformationError = Static<typeof SnippetTransformationError>

export const SnippetTransformationFailed = Object({
  errors: Array(SnippetTransformationError),
})
export type SnippetTransformationFailed = Static<typeof SnippetTransformationFailed>

export const CompilationError = Object({
  message: String,
  location: SourceLocation.optional(),
})
export type CompilationError = Static<typeof CompilationError>

export const CompilationFailed = Object({
  errors: Array(CompilationError),
})
export type CompilationFailed = Static<typeof CompilationFailed>

export const CheckstyleFailed = Object({
  errors: Array(CheckstyleError),
})
export type CheckstyleFailed = Static<typeof CheckstyleFailed>

export const KtlintFailed = Object({
  errors: Array(KtlintError),
})
export type KtlintFailed = Static<typeof KtlintFailed>

export const SourceError = Object({
  message: String,
  location: SourceLocation.optional(),
})
export type SourceError = Static<typeof SourceError>

export const ComplexityFailed = Object({
  errors: Array(SourceError),
})
export type ComplexityFailed = Static<typeof ComplexityFailed>

export const FeaturesFailed = Object({
  errors: Array(SourceError),
})
export type FeaturesFailed = Static<typeof FeaturesFailed>

export const MutationsFailed = Object({
  errors: Array(SourceError),
})
export type MutationsFailed = Static<typeof MutationsFailed>

export const DisassembleFailed = Object({
  message: String,
})
export type DisassembleFailed = Static<typeof DisassembleFailed>

export const ExecutionFailedResult = Object({
  classNotFound: String.optional(),
  methodNotFound: String.optional(),
})
export type ExecutionFailedResult = Static<typeof ExecutionFailedResult>

export const FailedTasks = Object({
  template: TemplatingFailed.optional(),
  snippet: SnippetTransformationFailed.optional(),
  compilation: CompilationFailed.optional(),
  kompilation: CompilationFailed.optional(),
  checkstyle: CheckstyleFailed.optional(),
  ktlint: KtlintFailed.optional(),
  complexity: ComplexityFailed.optional(),
  execution: ExecutionFailedResult.optional(),
  cexecution: ExecutionFailedResult.optional(),
  features: FeaturesFailed.optional(),
  mutations: MutationsFailed.optional(),
  disassemble: DisassembleFailed.optional(),
})
export type FailedTasks = Static<typeof FailedTasks>

export const Response = Object({
  request: Request,
  status: ServerStatus,
  completed: CompletedTasks,
  completedTasks: Array(Task),
  failed: FailedTasks,
  failedTasks: Array(Task),
  interval: Interval,
  email: String.optional(),
  audience: Array(String).optional(),
})
export type Response = Static<typeof Response>
