package main

import (
	"strings"
	"testing"
	"time"
)

func TestPresign(t *testing.T) {
	s := &S3{Host: "examplebucket.s3.amazonaws.com", Region: "us-east-1", Access: "AKIAIOSFODNN7EXAMPLE", Secret: "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
		Now: func() time.Time { return time.Date(2013, 5, 24, 0, 0, 0, 0, time.UTC) }}
	u := s.Presign("GET", "test.txt", 86400*time.Second)
	if !strings.HasSuffix(u, "X-Amz-Signature=aeeed9bbccd4d02ee5c0109b86d86835f995330da4c265957d157751f604d404") {
		t.Fatal(u)
	}
}

func TestSign(t *testing.T) {
	s := &S3{Host: "examplebucket.s3.amazonaws.com", Region: "us-east-1", Access: "AKIAIOSFODNN7EXAMPLE", Secret: "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
		Now: func() time.Time { return time.Date(2013, 5, 24, 0, 0, 0, 0, time.UTC) }}
	_, auth := s.sign("GET", "test.txt", nil, map[string]string{"Range": "bytes=0-9"})
	if !strings.HasSuffix(auth, "Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41") {
		t.Fatal(auth)
	}
}
