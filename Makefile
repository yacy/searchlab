install:
	cd ui; mkdocs build; cd ..
	./gradlew assemble
