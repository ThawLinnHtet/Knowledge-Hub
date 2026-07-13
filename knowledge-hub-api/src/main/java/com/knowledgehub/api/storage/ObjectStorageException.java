package com.knowledgehub.api.storage;

public class ObjectStorageException extends RuntimeException {

	public ObjectStorageException(String message) {
		super(message);
	}

	public ObjectStorageException(String message, Throwable cause) {
		super(message, cause);
	}
}
