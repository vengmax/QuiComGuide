document.addEventListener('DOMContentLoaded', function() {
  Array.from(document.querySelectorAll('.chat-table')).forEach(function(tbl) {
    tbl.setAttribute('role', 'table');
  });
});
