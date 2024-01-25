parser grammar SnippetParser;

import JavaParser;

options { tokenVocab=SnippetLexer; }

snippet
    : '{' snippetStatement* '}' EOF
    ;

snippetStatement
    : localVariableDeclaration ';'
    | statement
    | localTypeDeclaration
    | modifier* methodDeclaration
    | modifier* genericMethodDeclaration
    | importDeclaration
    ;
