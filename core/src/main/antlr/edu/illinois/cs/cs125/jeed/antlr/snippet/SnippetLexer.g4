lexer grammar SnippetLexer;

import JavaLexer;

// Set channel for SourceUtilities
WS:                 [ \t\r\n\u000C]+ -> channel(2);
COMMENT:            '/*' .*? '*/'    -> channel(1);
LINE_COMMENT:       '//' ~[\r\n]*    -> channel(1);
