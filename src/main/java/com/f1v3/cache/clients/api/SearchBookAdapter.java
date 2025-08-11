package com.f1v3.cache.clients.api;


import com.f1v3.cache.clients.api.response.SearchBookDTO;

public interface SearchBookAdapter {
    SearchBookDTO search(String query, int page);
}
