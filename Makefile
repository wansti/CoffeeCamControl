# Makefile for CoffeeCamControl
CLIENT=CoffeeCamControl
SERVER=CoffeeCamServer

all: $(CLIENT).jar $(SERVER).class

$(CLIENT).jar: $(CLIENT).java $(CLIENT).cfg manifest_client.txt 
	javac $(CLIENT).java
	jar cfm $(CLIENT).jar manifest_client.txt $(CLIENT).cfg $(CLIENT)*.class

$(SERVER).class: $(SERVER).java
	javac $(SERVER).java

clean:
	rm -f *.jar *.class
