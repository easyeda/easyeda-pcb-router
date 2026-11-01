package com.easyeda.router.engine;

import java.io.IOException;

/** Invalid board input reported asynchronously by an isolated engine. */
public final class EngineInputException extends IOException {

	public EngineInputException(String message) { super(message); }
}
