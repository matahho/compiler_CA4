.class public Main
.super java/lang/Object
.method public <init>()V
.limit stack 128
.limit locals 128
		aload_0
		invokespecial java/lang/Object/<init>()V
		invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;
		astore 1
		new Fptr
		dup
		aload_0
		ldc "Print"
		invokespecial Fptr/<init>(Ljava/lang/Object;Ljava/lang/String;)V
		return
.end method
.method public static main([Ljava/lang/String;)V
.limit stack 128
.limit locals 128
		new Main
		invokespecial Main/<init>()V
		return
.end method
