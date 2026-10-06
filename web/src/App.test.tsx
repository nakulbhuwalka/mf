import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, expect, it } from "vitest";
import App from "./App";

afterEach(cleanup);

it("renders the Mutual Funds heading", () => {
  render(<App />);

  expect(screen.getByRole("heading", { name: "Mutual Funds" })).toBeTruthy();
});
