parser grammar SnippetParser;

import JavaParser;

options { tokenVocab=SnippetLexer; }

snippet
    : '{' snippetStatement* '}' EOF
    ;

snippetStatement
    : localVariableDeclaration ';'
    | statement
    | typeDeclaration
    | modifier* methodDeclaration
    | modifier* genericMethodDeclaration
    | importDeclaration
    ;
