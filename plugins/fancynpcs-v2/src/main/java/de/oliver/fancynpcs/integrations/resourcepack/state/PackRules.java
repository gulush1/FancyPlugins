package de.oliver.fancynpcs.integrations.resourcepack.state;

/**
 * Snapshot of the status-chain rules used to classify a pack answer: strict-chain enforcement and the
 * proxy-answer detection window.
 */
public record PackRules(boolean strictChain, long proxyAnswerWindowMillis) {
}
