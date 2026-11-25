package main

import (
	"net/netip"
	"testing"
)

func TestTunnelDNSUsesConfiguredResolversAndFallbacks(t *testing.T) {
	got := tunnelDNS("9.9.9.9, 2001:4860:4860::8888,9.9.9.9")
	want := []netip.Addr{
		netip.MustParseAddr("9.9.9.9"),
		netip.MustParseAddr("2001:4860:4860::8888"),
		netip.MustParseAddr("1.1.1.1"),
		netip.MustParseAddr("1.0.0.1"),
	}
	if len(got) != len(want) {
		t.Fatalf("got %v, want %v", got, want)
	}
	for i := range want {
		if got[i] != want[i] {
			t.Fatalf("got %v, want %v", got, want)
		}
	}
}

func TestTunnelDNSFallsBackForEmptyOrInvalidInput(t *testing.T) {
	for _, raw := range []string{"", "not-an-ip,999.999.999.999"} {
		got := tunnelDNS(raw)
		if len(got) != 2 || got[0].String() != "1.1.1.1" || got[1].String() != "1.0.0.1" {
			t.Fatalf("tunnelDNS(%q) = %v", raw, got)
		}
	}
}
