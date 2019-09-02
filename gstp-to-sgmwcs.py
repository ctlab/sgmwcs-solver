import sys
import glob

class ToSgmwcs(object):

  def __init__(self, cords, edges, terms, out_path, name):
    edges_out = open(out_path + '/edges_{}'.format(name), 'w')
    nodes_out = open(out_path + '/nodes_{}'.format(name), 'w')
    sigs_out = open(out_path + '/signals_{}'.format(name), 'w')
    ss = {}
    for t in terms:
      ss['S' + str(t)] = 0
    ss['S0'] = 0
    for n1 in range(1, len(edges)):
      is_gr = 0
      for (n2, w) in edges[n1]:
        if n2 not in terms:
          signum = 1
          while 'S{}'.format(signum) in ss:
            signum += 1
          newsig = 'S{}'.format(signum)
          ss[newsig] = -w
          edges_out.write(str(n1) + ' ' + str(n2) + ' ' + newsig + '\n')
        else:
          ss['S{}'.format(n2)] = w
          is_gr = 1
          nodes_out.write('{} S{}\n'.format(n1, n2))
      if not is_gr:
        nodes_out.write('{} S0\n'.format(n1))
    # negsum = 0
    # for (s, w) in ss.items():
    # if w < 0:
    #   negsum -= w
    for (s, w) in ss.items():
      if w > 0:
        ss[s] = 'inf'
    for (s, w) in ss.items():
      sigs_out.write(s + ' ' + str(w) + '\n')
    for h in (edges_out, nodes_out, sigs_out):
      h.close()
  
class StpReader(object):

  def __init__(self, stp_path):
    self._f_handle = open(stp_path)

  def read(self):
    (self.cords, self.edges, self.terms) = self.read_stp()
    self.__close__()
  
  def __close__(self):
    self._f_handle.close()

  def read_stp(self):
    self.open_section()
    self.open_section()
    n, e = self.read_graph_size()
    edges = self.read_edges(e, n)
    self.open_section()
    terms = self.read_terminals()
    self.open_section()
    cs = self.read_cords(n)
    return (cs, edges, terms)
      
  def open_section(self):
    while not 'Section' in self._f_handle.readline():
      pass

  def read_graph_size(self):
    nodes = int(self._f_handle.readline().split()[1])
    edges = int(self._f_handle.readline().split()[1])
    return (nodes, edges)

  def read_edges(self, num_edges, num_nodes):
    inc_list = [[] for _ in range(num_nodes + 1)]
    for _ in range(num_edges):
      n1, n2, w = self.read_edge()
      inc_list[n1].append((n2, w))
    return inc_list

  def read_edge(self):
    _, n1, n2, w = self._f_handle.readline().split()
    return (int(n1), int(n2), int(w))

  def read_terminals(self):
    t_size = int(self._f_handle.readline().split()[1])
    terminals = set()
    for _ in range(t_size):
      terminals.add(int(self._f_handle.readline().split()[1]))
    return terminals

  def read_cords(self, num_nodes):
    cords = {}
    for _ in range(num_nodes):
      _, n, x, y = self._f_handle.readline().split()
      cords[int(n)] = (int(x), int(y))
    return cords



def convert(stp_file):
  reader = StpReader(stp_file)
  reader.read()
  path = stp_file.split('/')
  path, name = path[:-1], path[-1]
  path = '/'.join(path) + '/'
  ToSgmwcs(reader.cords, reader.edges, reader.terms, path, name)

def main(stp_path):
  for f in glob.glob(stp_path):
    convert(f)


if __name__ == '__main__':
  main('/Developer/sgmwcs/sgmwcs-solver/stp-samples/*.stp')
